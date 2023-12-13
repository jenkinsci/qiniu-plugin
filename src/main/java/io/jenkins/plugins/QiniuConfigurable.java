package io.jenkins.plugins;

import javax.annotation.Nonnull;

import hudson.util.Secret;

interface QiniuConfigurable {
    @Nonnull
    public String getAccessKey();

    @Nonnull
    public Secret getSecretKey();

    @Nonnull
    public String getBucketName();

    @Nonnull
    public String getObjectNamePrefix();

    @Nonnull
    public String getDownloadDomain();

    @Nonnull
    public String getUpDomain();

    @Nonnull
    public String getRsDomain();

    @Nonnull
    public String getRsfDomain();

    @Nonnull
    public String getUcDomain();

    @Nonnull
    public String getApiDomain();

    public boolean isUseHTTPs();

    public boolean isInfrequentStorage();

    public boolean isDeleteArtifacts();

    public boolean isApplyForAllJobs();

    public int getMultipartUploadConcurrency();

    public int getMultipartUploadPartSize();

    public int getMultipartUploadThreshold();

    public int getConnectTimeout();

    public int getReadTimeout();

    public int getWriteTimeout();

    public int getRetryCount();
}
