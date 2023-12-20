package io.jenkins.plugins;

import java.io.Serializable;

import javax.annotation.Nonnull;

import com.qiniu.storage.BucketManager;
import com.qiniu.storage.Configuration;
import com.qiniu.storage.Configuration.ResumableUploadAPIVersion;
import com.qiniu.storage.Region;
import com.qiniu.util.Auth;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Util;
import hudson.util.Secret;

public final class QiniuConfig implements Serializable, QiniuConfigurable {
    private static final long serialVersionUID = 3L;
    @Nonnull
    private final String accessKey;
    @Nonnull
    private final Secret secretKey;
    @Nonnull
    private final String bucketName, objectNamePrefix, downloadDomain, upDomain;
    @Nonnull
    private final String rsDomain, rsfDomain, ucDomain, apiDomain;

    private final boolean useHTTPs, deleteArtifacts, applyForAllJobs;
    private final int fileType;
    private final int multipartUploadConcurrency, multipartUploadPartSize, multipartUploadThreshold;
    private final int connectTimeout, readTimeout, writeTimeout, retryCount;

    private static final String DEFAULT_RS_HOST = Configuration.defaultRsHost;
    private static final String DEFAULT_API_HOST = Configuration.defaultApiHost;
    private static final String DEFAULT_UC_HOST = Configuration.defaultUcHost;

    public QiniuConfig(@Nonnull final String accessKey, @Nonnull final Secret secretKey,
            @Nonnull final String bucketName, @Nonnull final String objectNamePrefix,
            @Nonnull final String downloadDomain, @Nonnull final String upDomain, @Nonnull final String rsDomain,
            @Nonnull final String rsfDomain, @Nonnull final String ucDomain, @Nonnull final String apiDomain,
            final boolean useHTTPs, final int fileType, final boolean deleteArtifacts, final boolean applyForAllJobs,
            final int multipartUploadConcurrency, final int multipartUploadPartSize, final int multipartUploadThreshold,
            final int connectTimeout, final int readTimeout, final int writeTimeout, final int retryCount) {
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.bucketName = bucketName;
        this.objectNamePrefix = objectNamePrefix;
        this.downloadDomain = downloadDomain;
        this.upDomain = upDomain;
        this.rsDomain = rsDomain;
        this.rsfDomain = rsfDomain;
        this.ucDomain = ucDomain;
        this.apiDomain = apiDomain;
        this.useHTTPs = useHTTPs;
        this.fileType = fileType;
        this.deleteArtifacts = deleteArtifacts;
        this.applyForAllJobs = applyForAllJobs;
        this.multipartUploadConcurrency = multipartUploadConcurrency;
        this.multipartUploadPartSize = multipartUploadPartSize;
        this.multipartUploadThreshold = multipartUploadThreshold;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        this.writeTimeout = writeTimeout;
        this.retryCount = retryCount;
    }

    @Nonnull
    public BucketManager getBucketManager() {
        Initializer.setAppName();
        return new BucketManager(this.getAuth(), this.getConfiguration());
    }

    @Nonnull
    public Auth getAuth() {
        final Auth auth = Auth.create(this.accessKey, this.secretKey.getPlainText());
        if (auth == null) {
            throw new RuntimeException("Failed to create Auth");
        }
        return auth;
    }

    @Nonnull
    @SuppressFBWarnings(value = "ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD", justification = "I must set static variable here")
    public Configuration getConfiguration() {
        final String rsDomain = Util.fixEmptyAndTrim(this.rsDomain);
        final String ucDomain = Util.fixEmptyAndTrim(this.ucDomain);
        final String apiDomain = Util.fixEmptyAndTrim(this.apiDomain);
        final String upDomain = Util.fixEmptyAndTrim(this.upDomain);
        final String rsfDomain = Util.fixEmptyAndTrim(this.rsfDomain);

        if (rsDomain != null && !Configuration.defaultRsHost.equals(rsDomain)) {
            Configuration.defaultRsHost = rsDomain;
        } else if (rsDomain == null) {
            Configuration.defaultRsHost = DEFAULT_RS_HOST;
        }

        if (ucDomain != null && !Configuration.defaultUcHost.equals(ucDomain)) {
            Configuration.defaultUcHost = ucDomain;
        } else if (ucDomain == null) {
            Configuration.defaultUcHost = DEFAULT_UC_HOST;
        }

        if (apiDomain != null && !Configuration.defaultApiHost.equals(apiDomain)) {
            Configuration.defaultApiHost = apiDomain;
        } else if (apiDomain == null) {
            Configuration.defaultApiHost = DEFAULT_API_HOST;
        }

        final Configuration config = new Configuration();
        config.resumableUploadAPIVersion = ResumableUploadAPIVersion.V2;
        config.resumableUploadAPIV2BlockSize = this.multipartUploadPartSize * 1024 * 1024;
        config.resumableUploadMaxConcurrentTaskCount = this.multipartUploadConcurrency;
        config.putThreshold = this.multipartUploadThreshold * 1024 * 1024;
        config.connectTimeout = this.connectTimeout;
        config.readTimeout = this.readTimeout;
        config.writeTimeout = this.writeTimeout;
        config.useHttpsDomains = this.useHTTPs;
        config.retryMax = this.retryCount;
        config.region = mayCreateRegion(upDomain, rsDomain, rsfDomain, apiDomain);
        return config;
    }

    private Region mayCreateRegion(final String upDomain, final String rsDomain, final String rsfDomain,
            final String apiDomain) {
        boolean returnsNull = true;
        Region.Builder regionBuilder = new Region.Builder();
        if (upDomain != null) {
            regionBuilder = regionBuilder.accUpHost(upDomain).srcUpHost(upDomain);
            returnsNull = false;
        }
        if (rsDomain != null) {
            regionBuilder = regionBuilder.rsHost(rsDomain);
            returnsNull = false;
        }
        if (rsfDomain != null) {
            regionBuilder = regionBuilder.rsfHost(rsfDomain);
            returnsNull = false;
        }
        if (apiDomain != null) {
            regionBuilder = regionBuilder.apiHost(apiDomain);
            returnsNull = false;
        }
        if (returnsNull) {
            return null;
        }
        return regionBuilder.build();
    }

    @Nonnull
    public String getAccessKey() {
        return this.accessKey;
    }

    @Nonnull
    public Secret getSecretKey() {
        return this.secretKey;
    }

    @Nonnull
    public String getBucketName() {
        return this.bucketName;
    }

    @Nonnull
    public String getObjectNamePrefix() {
        return this.objectNamePrefix;
    }

    @Nonnull
    public String getDownloadDomain() {
        return this.downloadDomain;
    }

    @Nonnull
    public String getUpDomain() {
        return this.upDomain;
    }

    @Nonnull
    public String getRsDomain() {
        return this.rsDomain;
    }

    @Nonnull
    public String getRsfDomain() {
        return this.rsfDomain;
    }

    @Nonnull
    public String getUcDomain() {
        return this.ucDomain;
    }

    @Nonnull
    public String getApiDomain() {
        return this.apiDomain;
    }

    public boolean isUseHTTPs() {
        return this.useHTTPs;
    }

    public boolean isDeleteArtifacts() {
        return this.deleteArtifacts;
    }

    public boolean isApplyForAllJobs() {
        return this.applyForAllJobs;
    }

    public int getFileType() {
        return this.fileType;
    }

    public int getMultipartUploadConcurrency() {
        return this.multipartUploadConcurrency;
    }

    public int getMultipartUploadPartSize() {
        return this.multipartUploadPartSize;
    }

    public int getMultipartUploadThreshold() {
        return this.multipartUploadThreshold;
    }

    public int getConnectTimeout() {
        return this.connectTimeout;
    }

    public int getReadTimeout() {
        return this.readTimeout;
    }

    public int getWriteTimeout() {
        return this.writeTimeout;
    }

    public int getRetryCount() {
        return this.retryCount;
    }
}
