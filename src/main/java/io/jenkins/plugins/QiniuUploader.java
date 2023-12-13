package io.jenkins.plugins;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import com.qiniu.storage.Configuration;
import com.qiniu.storage.UploadManager;
import com.qiniu.util.Auth;
import com.qiniu.util.StringMap;

import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import jenkins.MasterToSlaveFileCallable;

@Restricted(NoExternalUse.class)
final class QiniuUploader extends MasterToSlaveFileCallable<Void> {
    private static final Logger LOG = Logger.getLogger(QiniuUploader.class.getName());

    @Nonnull
    private final String objectNamePrefix;
    @Nonnull
    private final QiniuConfig config;
    @Nonnull
    private final Map<String, String> artifactURLs;
    private final TaskListener listener;

    QiniuUploader(@Nonnull QiniuConfig config, @Nonnull Map<String, String> artifactURLs,
            @Nonnull String objectNamePrefix, TaskListener listener) {
        this.config = config;
        this.artifactURLs = artifactURLs;
        this.objectNamePrefix = objectNamePrefix;
        this.listener = listener;
    }

    @Override
    public Void invoke(File root, VirtualChannel virtualChannel) throws IOException, InterruptedException {
        if (this.artifactURLs.isEmpty()) {
            return null;
        }

        Initializer.setAppName();

        try {
            this.deleteFiles();
            this.uploadFiles(root);
        } finally {
            if (this.listener != null) {
                this.listener.getLogger().flush();
            }
        }
        LOG.log(Level.INFO, "Qiniu uploading is done");
        return null;
    }

    private void deleteFiles() throws IOException {
        QiniuUtils.deletePrefix(this.config.getBucketManager(), this.config.getBucketName(), this.objectNamePrefix);
        LOG.log(Level.INFO, "Qiniu pre-clean {0} done", new Object[] { this.objectNamePrefix });
    }

    private void uploadFiles(final File root) throws IOException {
        final Configuration config = this.config.getConfiguration();
        final UploadManager uploadManager = new UploadManager(config);
        StringMap params = new StringMap().put("insertOnly", 1);
        if (this.config.isInfrequentStorage()) {
            params = params.put("fileType", 1);
        }
        final Auth auth = Auth.create(this.config.getAccessKey(), this.config.getSecretKey().getPlainText());
        final String uploadToken = auth.uploadToken(this.config.getBucketName(), null, 24 * 3600, params);
        for (Map.Entry<String, String> entry : this.artifactURLs.entrySet()) {
            final String objectName = this.objectNamePrefix + entry.getValue();
            final File file = new File(root, entry.getKey());
            uploadManager.put(file, objectName, uploadToken, null, null, true);
            LOG.log(Level.INFO, "Qiniu upload {0} to {1}", new Object[] { file.getAbsolutePath(), objectName });
        }
    }
}
