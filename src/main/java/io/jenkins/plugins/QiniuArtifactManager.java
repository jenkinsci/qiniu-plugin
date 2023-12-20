package io.jenkins.plugins;

import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import com.qiniu.common.QiniuException;
import com.qiniu.storage.BucketManager;
import com.qiniu.storage.UploadManager;
import com.qiniu.util.Auth;
import com.qiniu.util.StringMap;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.Run;
import jenkins.model.ArtifactManager;
import jenkins.model.StandardArtifactManager;
import jenkins.util.VirtualFile;

@Restricted(NoExternalUse.class)
public final class QiniuArtifactManager extends ArtifactManager {
    private static final Logger LOG = Logger.getLogger(QiniuArtifactManager.class.getName());

    @Nonnull
    private final QiniuConfig config;
    @Nonnull
    private Marker marker;
    @Nonnull
    private StandardArtifactManager standardArtifactManager;
    @Nonnull
    private String objectNamePrefixWithBuildNumber;

    public QiniuArtifactManager(Run<?, ?> run, @Nonnull QiniuConfig config) {
        this.config = config;
        this.objectNamePrefixWithBuildNumber = this.generateObjectNamePrefixWithBuildNumber(run);
        this.standardArtifactManager = new StandardArtifactManager(run);
        this.marker = new Marker(this.objectNamePrefixWithBuildNumber, this.config);
        LOG.log(Level.INFO, "QiniuArtifactManager is constructed, accessKey={0}, prefix={1}",
                new Object[] { this.config.getAccessKey(), this.objectNamePrefixWithBuildNumber });
    }

    @Override
    public void onLoad(Run<?, ?> run) {
        this.objectNamePrefixWithBuildNumber = this.generateObjectNamePrefixWithBuildNumber(run);
        this.standardArtifactManager = new StandardArtifactManager(run);
        this.marker = new Marker(this.objectNamePrefixWithBuildNumber, this.config);
        LOG.log(Level.INFO, "QiniuArtifactManager is ready, prefix={0}", this.objectNamePrefixWithBuildNumber);
    }

    @Nonnull
    private String generateObjectNamePrefixWithBuildNumber(Run<?, ?> run) {
        String n = this.config.getObjectNamePrefix();
        if (!n.isEmpty() && !n.endsWith(QiniuFileSystem.SEPARATOR)) {
            n += QiniuFileSystem.SEPARATOR;
        }
        if (run != null) {
            n += run.getParent().getFullName();
            n += QiniuFileSystem.SEPARATOR;
            n += run.getId();
            n += QiniuFileSystem.SEPARATOR;
        }
        return n;
    }

    @Override
    public void archive(FilePath workspace, Launcher launcher, BuildListener buildListener, Map<String, String> pathMap)
            throws IOException, InterruptedException {
        if (!this.config.isApplyForAllJobs() && !this.marker.didUseQiniuArtifactArchiver()) {
            LOG.log(Level.INFO, "StandardArtifactManager::archive()");
            this.standardArtifactManager.archive(workspace, launcher, buildListener, pathMap);
            return;
        } else if (this.config.isApplyForAllJobs()) {
            this.marker.useQiniuArtifactArchiver();
        }

        LOG.log(Level.INFO, "QiniuArtifactManager::archive()");
        final Map<String, String> artifacts = new HashMap<>();
        for (Map.Entry<String, String> entry : pathMap.entrySet()) {
            final String objectNameWithoutPrefix = entry.getKey();
            final String filePath = QiniuFileSystem.fromFileSystemPathToObjectName(entry.getValue());
            artifacts.put(objectNameWithoutPrefix, filePath);
        }
        workspace.act(new QiniuUploader(this.config, artifacts, this.objectNamePrefixWithBuildNumber, buildListener));
    }

    @Override
    public boolean delete() throws IOException, InterruptedException {
        if (!this.config.isDeleteArtifacts()) {
            return false;
        }
        if (!this.marker.didUseQiniuArtifactArchiver()) {
            LOG.log(Level.INFO, "StandardArtifactManager::delete()");
            return this.standardArtifactManager.delete();
        }
        LOG.log(Level.INFO, "QiniuArtifactManager::delete()");

        final QiniuFile qiniuFile = (QiniuFile) this.root();
        final boolean result = qiniuFile.deleteRecursively();
        this.marker.deleteQiniuArtifactArchiverMark();
        return result;
    }

    @Override
    public VirtualFile root() {
        if (!this.marker.didUseQiniuArtifactArchiver()) {
            LOG.log(Level.INFO, "StandardArtifactManager::root()");
            return this.standardArtifactManager.root();
        }
        LOG.log(Level.INFO, "QiniuArtifactManager::root(): prefix={0}", this.objectNamePrefixWithBuildNumber);
        final QiniuFileSystem qiniuFileSystem = QiniuFileSystem.create(this.config,
                this.objectNamePrefixWithBuildNumber);
        return new QiniuFile(qiniuFileSystem, null);
    }

    @Nonnull
    public Marker getMarker() {
        return this.marker;
    }

    public static final class Marker implements Serializable {
        private static final long serialVersionUID = 2L;
        @Nonnull
        private final String objectName;
        @Nonnull
        private final QiniuConfig config;

        private Marker(@Nonnull final String objectNamePrefix, @Nonnull final QiniuConfig config) {
            this.objectName = this.getMarkObjectName(objectNamePrefix);
            this.config = config;
        }

        public boolean didUseQiniuArtifactArchiver() {
            final BucketManager bucketManager = this.config.getBucketManager();
            try {
                bucketManager.stat(this.config.getBucketName(), this.objectName);
                return true;
            } catch (QiniuException e) {
                LOG.log(Level.ALL, "Failed to detect qiniu artifact archiver mark: {0}", e);
                return false;
            }
        }

        public void useQiniuArtifactArchiver() throws IOException {
            final UploadManager uploadManager = new UploadManager(this.config.getConfiguration());
            final Auth auth = Auth.create(this.config.getAccessKey(), this.config.getSecretKey().getPlainText());
            final String uploadToken = auth.uploadToken(this.config.getBucketName(), null, 24 * 3600,
                    new StringMap().put("insertOnly", 0));
            Initializer.setAppName();
            uploadManager.put("{}".getBytes("UTF-8"), this.objectName, uploadToken, null, null, true);
        }

        public void deleteQiniuArtifactArchiverMark() throws IOException {
            this.config.getBucketManager().delete(this.config.getBucketName(), this.objectName);
        }

        @Nonnull
        private String getMarkObjectName(@Nonnull final String objectNamePrefix) {
            String name = objectNamePrefix;
            while (name.endsWith(QiniuFileSystem.SEPARATOR)) {
                name = name.substring(0, name.length() - 1);
            }
            return name + ".qiniu-artifact-archiver";
        }
    }
}
