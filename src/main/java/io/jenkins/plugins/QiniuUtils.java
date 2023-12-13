package io.jenkins.plugins;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

import com.qiniu.storage.BucketManager;
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
        final int[] counter = new int[] { 0 };
        listPrefix(bucketManager, bucketName, prefix, (FileInfo fileInfo) -> {
            batch.addDeleteOp(bucketName, fileInfo.key);
            LOG.log(Level.INFO, "QiniuUtils::delete(), bucket={0}, key={1}", new Object[] { bucketName, fileInfo.key });
            counter[0]++;
            if (counter[0] >= 1000) {
                bucketManager.batch(batch);
                batch.clearOps();
                counter[0] = 0;
            }
        });
        if (counter[0] > 0) {
            bucketManager.batch(batch);
        }
    }
}
