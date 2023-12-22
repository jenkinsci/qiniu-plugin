package io.jenkins.plugins;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

import com.qiniu.http.Response;
import com.qiniu.storage.BucketManager;
import com.qiniu.storage.model.BatchStatus;
import com.qiniu.storage.model.FileInfo;
import com.qiniu.storage.model.FileListing;

final class QiniuUtils {
    private static final Logger LOG = Logger.getLogger(QiniuUtils.class.getName());

    static void listPrefix(
            @Nonnull final BucketManager bucketManager,
            @Nonnull final String bucketName,
            @Nonnull final String prefix,
            @Nonnull final FileInfoConsumer consumer) throws IOException {
        String marker = null;
        for (;;) {
            LOG.log(Level.INFO, "QiniuUtils::listPrefix(), bucket={0}, prefix={1}, marker={2}",
                    new Object[] { bucketName, prefix, marker });
            final FileListing list = bucketManager.listFiles(bucketName, prefix, marker, 1000, null);
            if (list.items != null) {
                for (FileInfo metadata : list.items) {
                    consumer.accept(metadata);
                }
            }
            marker = list.marker;
            if (marker == null || marker.isEmpty()) {
                break;
            }
        }
    }

    @FunctionalInterface
    static interface FileInfoConsumer {
        void accept(FileInfo fileInfo) throws IOException;
    }

    static void deletePrefix(
            @Nonnull final BucketManager bucketManager,
            @Nonnull final String bucketName,
            @Nonnull final String prefix) throws IOException {
        final BucketManager.BatchOperations batch = new BucketManager.BatchOperations();
        final List<String> keys = new ArrayList<String>(1000);
        listPrefix(bucketManager, bucketName, prefix, (FileInfo fileInfo) -> {
            batch.addDeleteOp(bucketName, fileInfo.key);
            keys.add(fileInfo.key);
            LOG.log(Level.INFO, "QiniuUtils::delete(), bucket={0}, key={1}", new Object[] { bucketName, fileInfo.key });
            if (keys.size() >= 1000) {
                checkBatchResponse(bucketManager.batch(batch), keys);
                batch.clearOps();
                keys.clear();
            }
        });
        if (!keys.isEmpty()) {
            checkBatchResponse(bucketManager.batch(batch), keys);
            batch.clearOps();
            keys.clear();
        }
    }

    static private void checkBatchResponse(final Response response, final List<String> keys) throws IOException {
        if (response == null) {
            return;
        }

        final BatchStatus[] batchStatusList = response.jsonToObject(BatchStatus[].class);
        for (int i = 0; i < batchStatusList.length; i++) {
            final BatchStatus status = batchStatusList[i];
            if (status.code == 200 || status.code == 612) {
                continue;
            }
            throw new IOException(String.format("Delete error %s: %s", keys.get(i), status.data.error));
        }
    }
}
