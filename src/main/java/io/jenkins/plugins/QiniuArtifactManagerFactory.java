package io.jenkins.plugins;

import java.io.IOException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.servlet.ServletException;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import com.qiniu.common.QiniuException;
import com.qiniu.storage.BucketManager;
import com.qiniu.storage.Configuration;
import com.qiniu.storage.Configuration.ResumableUploadAPIVersion;
import com.qiniu.storage.Region;
import com.qiniu.util.Auth;

import hudson.Extension;
import hudson.Util;
import hudson.model.Run;
import hudson.util.FormValidation;
import hudson.util.Secret;
import jenkins.model.ArtifactManager;
import jenkins.model.ArtifactManagerFactory;
import jenkins.model.ArtifactManagerFactoryDescriptor;
import jenkins.model.Jenkins;

@Restricted(NoExternalUse.class)
public final class QiniuArtifactManagerFactory extends ArtifactManagerFactory implements QiniuConfigurable {
    public static final boolean applyForAllJobs = false;
    private static final Logger LOG = Logger.getLogger(QiniuArtifactManagerFactory.class.getName());
    private static final String DEFAULT_RS_HOST = Configuration.defaultRsHost;
    private static final String DEFAULT_API_HOST = Configuration.defaultApiHost;
    private static final String DEFAULT_UC_HOST = Configuration.defaultUcHost;

    @Nonnull
    private final QiniuConfig config;

    @DataBoundConstructor
    public QiniuArtifactManagerFactory(@Nonnull String accessKey, @Nonnull final Secret secretKey,
            @Nonnull String bucketName, @Nonnull String objectNamePrefix, @Nonnull String downloadDomain,
            @Nonnull String rsDomain, @Nonnull String rsfDomain, @Nonnull String ucDomain, @Nonnull String apiDomain,
            @Nonnull String upDomain,
            final boolean useHTTPs, final boolean infrequentStorage, final boolean deleteArtifacts,
            int multipartUploadConcurrency, int multipartUploadPartSize,
            int multipartUploadThreshold, int connectTimeout,
            int readTimeout, int writeTimeout, int retryCount) {
        accessKey = Util.fixEmptyAndTrim(accessKey);
        bucketName = Util.fixEmptyAndTrim(bucketName);
        downloadDomain = Util.fixEmptyAndTrim(downloadDomain);
        upDomain = Util.fixEmptyAndTrim(upDomain);
        rsDomain = Util.fixEmptyAndTrim(rsDomain);
        rsfDomain = Util.fixEmptyAndTrim(rsfDomain);
        ucDomain = Util.fixEmptyAndTrim(ucDomain);
        apiDomain = Util.fixEmptyAndTrim(apiDomain);

        if (accessKey == null) {
            throw new IllegalArgumentException("accessKey must not be null or empty");
        } else if (secretKey == null || Util.fixEmptyAndTrim(secretKey.getPlainText()) == null) {
            throw new IllegalArgumentException("secretKey must not be null or empty");
        } else if (bucketName == null) {
            throw new IllegalArgumentException("bucketName must not be null or empty");
        }

        if (multipartUploadConcurrency == 0) {
            multipartUploadConcurrency = 1;
        } else if (multipartUploadConcurrency < 0) {
            throw new IllegalArgumentException("multipartUploadConcurrency must be valid positive integer");
        }

        if (multipartUploadPartSize == 0) {
            multipartUploadPartSize = 4;
        } else if (multipartUploadPartSize < 0) {
            throw new IllegalArgumentException("multipartUploadPartSize must be valid positive integer");
        }

        if (multipartUploadThreshold == 0) {
            multipartUploadThreshold = 4;
        } else if (multipartUploadThreshold < 0) {
            throw new IllegalArgumentException("multipartUploadThreshold must be valid positive integer");
        }

        if (connectTimeout == 0) {
            connectTimeout = 5;
        } else if (connectTimeout < 0) {
            throw new IllegalArgumentException("connectTimeout must be valid positive integer");
        }

        if (readTimeout == 0) {
            readTimeout = 30;
        } else if (readTimeout < 0) {
            throw new IllegalArgumentException("readTimeout must be valid positive integer");
        }

        if (writeTimeout == 0) {
            writeTimeout = 30;
        } else if (writeTimeout < 0) {
            throw new IllegalArgumentException("writeTimeout must be valid positive integer");
        }

        if (retryCount == 0) {
            retryCount = 10;
        } else if (retryCount < 0) {
            throw new IllegalArgumentException("retryCount must be valid positive integer");
        }

        if (upDomain == null) {
            upDomain = "";
        }
        if (rsDomain == null) {
            rsDomain = "";
        }
        if (rsfDomain == null) {
            rsfDomain = "";
        }
        if (ucDomain == null) {
            ucDomain = "";
        }
        if (apiDomain == null) {
            apiDomain = "";
        }
        if (downloadDomain == null) {
            downloadDomain = "";
        }
        QiniuConfig config = new QiniuConfig(accessKey, secretKey, bucketName, objectNamePrefix, downloadDomain,
                upDomain, rsDomain, rsfDomain, ucDomain, apiDomain, useHTTPs, infrequentStorage, deleteArtifacts,
                applyForAllJobs, multipartUploadConcurrency, multipartUploadPartSize, multipartUploadThreshold,
                connectTimeout, readTimeout, writeTimeout, retryCount);
        if (downloadDomain.isEmpty()) {
            boolean couldUseDefaultIoSrc = false;
            try {
                final String defaultIoSrc = config.getBucketManager().getDefaultIoSrcHost(config.getBucketName());
                couldUseDefaultIoSrc = defaultIoSrc != null && !defaultIoSrc.isEmpty();
            } catch (QiniuException e) {
                // do nothing
            }
            if (!couldUseDefaultIoSrc) {
                try {
                    final String[] domainList = config.getBucketManager().domainList(config.getBucketName());
                    String defaultDomain = null;
                    if (domainList.length > 0) {
                        defaultDomain = domainList[domainList.length - 1];
                    }
                    if (defaultDomain != null) {
                        config = new QiniuConfig(accessKey, secretKey, bucketName,
                                objectNamePrefix, defaultDomain,
                                upDomain, rsDomain, rsfDomain, ucDomain, apiDomain,
                                useHTTPs, infrequentStorage, deleteArtifacts, applyForAllJobs,
                                multipartUploadConcurrency, multipartUploadPartSize,
                                multipartUploadThreshold,
                                connectTimeout, readTimeout, writeTimeout, retryCount);
                    } else {
                        throw new CannotGetDownloadDomain(
                                "Bucket " + config.getBucketName() + " are not bound with any download domain");
                    }
                } catch (QiniuException e) {
                    throw new QiniuRuntimeException(e);
                }
            }
        }
        this.config = config;
        if (downloadDomain != "") {
            LOG.log(Level.INFO,
                    "QiniuArtifactManagerFactory is configured: accessKey={0}, bucketName={1}, downloadDomain={2}",
                    new Object[] { accessKey, bucketName, downloadDomain });
        } else {
            LOG.log(Level.INFO,
                    "QiniuArtifactManagerFactory is configured: accessKey={0}, bucketName={1}, useDefaultIoSrcDomain=true",
                    new Object[] { accessKey, bucketName });
        }
    }

    @Nonnull
    @Override
    public ArtifactManager managerFor(Run<?, ?> run) {
        LOG.log(Level.INFO, "QiniuArtifactManagerFactory creates QiniuArtifactManager");
        return new QiniuArtifactManager(run, this.config);
    }

    @CheckForNull
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) Jenkins.get().getDescriptor(this.getClass());
    }

    @Extension
    public static final class DescriptorImpl extends ArtifactManagerFactoryDescriptor {
        @Nonnull
        @Override
        public String getDisplayName() {
            return Messages.QiniuArtifactManagerFactory_DescriptorImpl_DisplayName();
        }

        @POST
        public FormValidation doCheckAccessKey(@QueryParameter String accessKey, @QueryParameter String bucketName,
                @QueryParameter String upDomain, @QueryParameter String rsDomain, @QueryParameter String rsfDomain,
                @QueryParameter String ucDomain, @QueryParameter String apiDomain,
                @QueryParameter final Secret secretKey, @QueryParameter final boolean useHTTPs)
                throws IOException, ServletException {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            accessKey = Util.fixEmptyAndTrim(accessKey);
            bucketName = Util.fixEmptyAndTrim(bucketName);
            upDomain = Util.fixEmptyAndTrim(upDomain);
            rsDomain = Util.fixEmptyAndTrim(rsDomain);
            rsfDomain = Util.fixEmptyAndTrim(rsfDomain);
            ucDomain = Util.fixEmptyAndTrim(ucDomain);
            apiDomain = Util.fixEmptyAndTrim(apiDomain);
            if (accessKey == null) {
                return FormValidation
                        .error(Messages.QiniuArtifactManagerFactory_DescriptorImpl_errors_accessKeyIsEmpty());
            }
            final Throwable err = this.checkAccessKeySecretKeyAndBucketName(accessKey, secretKey, bucketName, upDomain,
                    rsDomain, rsfDomain, ucDomain, apiDomain, useHTTPs);
            if (err == null) {
                return FormValidation.ok();
            } else {
                return FormValidation.error(err, Messages
                        .QiniuArtifactManagerFactory_DescriptorImpl_errors_invalidAccessKeySecretKeyAndBucketName());
            }
        }

        @POST
        public FormValidation doCheckSecretKey(@QueryParameter final Secret secretKey, @QueryParameter String accessKey,
                @QueryParameter String bucketName, @QueryParameter String upDomain, @QueryParameter String rsDomain,
                @QueryParameter String rsfDomain, @QueryParameter String ucDomain, @QueryParameter String apiDomain,
                @QueryParameter final boolean useHTTPs) throws IOException, ServletException {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            accessKey = Util.fixEmptyAndTrim(accessKey);
            bucketName = Util.fixEmptyAndTrim(bucketName);
            upDomain = Util.fixEmptyAndTrim(upDomain);
            rsDomain = Util.fixEmptyAndTrim(rsDomain);
            rsfDomain = Util.fixEmptyAndTrim(rsfDomain);
            ucDomain = Util.fixEmptyAndTrim(ucDomain);
            apiDomain = Util.fixEmptyAndTrim(apiDomain);
            if (secretKey == null || Util.fixEmptyAndTrim(secretKey.getPlainText()) == null) {
                return FormValidation
                        .error(Messages.QiniuArtifactManagerFactory_DescriptorImpl_errors_secretKeyIsEmpty());
            }
            final Throwable err = this.checkAccessKeySecretKeyAndBucketName(accessKey, secretKey, bucketName, upDomain,
                    rsDomain, rsfDomain, ucDomain, apiDomain, useHTTPs);
            if (err == null) {
                return FormValidation.ok();
            } else {
                return FormValidation.error(err, Messages
                        .QiniuArtifactManagerFactory_DescriptorImpl_errors_invalidAccessKeySecretKeyAndBucketName());
            }
        }

        @POST
        public FormValidation doCheckBucketName(@QueryParameter String bucketName, @QueryParameter String accessKey,
                @QueryParameter String upDomain, @QueryParameter String rsDomain, @QueryParameter String rsfDomain,
                @QueryParameter String ucDomain, @QueryParameter String apiDomain,
                @QueryParameter final Secret secretKey, @QueryParameter final boolean useHTTPs)
                throws IOException, ServletException {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            accessKey = Util.fixEmptyAndTrim(accessKey);
            bucketName = Util.fixEmptyAndTrim(bucketName);
            upDomain = Util.fixEmptyAndTrim(upDomain);
            rsDomain = Util.fixEmptyAndTrim(rsDomain);
            rsfDomain = Util.fixEmptyAndTrim(rsfDomain);
            ucDomain = Util.fixEmptyAndTrim(ucDomain);
            apiDomain = Util.fixEmptyAndTrim(apiDomain);
            if (bucketName == null) {
                return FormValidation
                        .error(Messages.QiniuArtifactManagerFactory_DescriptorImpl_errors_bucketNameIsEmpty());
            }
            final Throwable err = this.checkAccessKeySecretKeyAndBucketName(accessKey, secretKey, bucketName, upDomain,
                    rsDomain, rsfDomain, ucDomain, apiDomain, useHTTPs);
            if (err == null) {
                return FormValidation.ok();
            } else {
                return FormValidation.error(err, Messages
                        .QiniuArtifactManagerFactory_DescriptorImpl_errors_invalidAccessKeySecretKeyAndBucketName());
            }
        }

        @POST
        public FormValidation doCheckDownloadDomain(@QueryParameter String downloadDomain,
                @QueryParameter String accessKey, @QueryParameter String bucketName, @QueryParameter String upDomain,
                @QueryParameter String rsDomain, @QueryParameter String rsfDomain, @QueryParameter String ucDomain,
                @QueryParameter String apiDomain, @QueryParameter final Secret secretKey,
                @QueryParameter final boolean useHTTPs) throws IOException, ServletException {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            downloadDomain = Util.fixEmptyAndTrim(downloadDomain);
            accessKey = Util.fixEmptyAndTrim(accessKey);
            bucketName = Util.fixEmptyAndTrim(bucketName);
            upDomain = Util.fixEmptyAndTrim(upDomain);
            rsDomain = Util.fixEmptyAndTrim(rsDomain);
            rsfDomain = Util.fixEmptyAndTrim(rsfDomain);
            ucDomain = Util.fixEmptyAndTrim(ucDomain);
            apiDomain = Util.fixEmptyAndTrim(apiDomain);
            if (downloadDomain != null) {
                try {
                    new URL("http://" + downloadDomain).openConnection().connect();
                } catch (Exception err) {
                    return FormValidation.error(err,
                            Messages.QiniuArtifactManagerFactory_DescriptorImpl_errors_invalidDownloadDomain());
                }
            }

            if (!this.checkDownloadDomain(accessKey, secretKey, bucketName, downloadDomain, upDomain, rsDomain,
                    rsfDomain, ucDomain, apiDomain, useHTTPs)) {
                return FormValidation
                        .error(Messages.QiniuArtifactManagerFactory_DescriptorImpl_errors_downloadDomainIsEmpty());
            }

            return FormValidation.ok();
        }

        @POST
        public FormValidation doCheckUpDomain(@QueryParameter String upDomain) throws IOException, ServletException {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            upDomain = Util.fixEmptyAndTrim(upDomain);
            if (upDomain != null) {
                try {
                    new URL("http://" + upDomain).openConnection().connect();
                } catch (Exception err) {
                    return FormValidation.error(err,
                            Messages.QiniuArtifactManagerFactory_DescriptorImpl_errors_invalidUpDomain());
                }
            }
            return FormValidation.ok();
        }

        @POST
        public FormValidation doCheckRsDomain(@QueryParameter String rsDomain) throws IOException, ServletException {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            rsDomain = Util.fixEmptyAndTrim(rsDomain);
            if (rsDomain != null) {
                try {
                    new URL("http://" + rsDomain).openConnection().connect();
                } catch (Exception err) {
                    return FormValidation.error(err,
                            Messages.QiniuArtifactManagerFactory_DescriptorImpl_errors_invalidRsDomain());
                }
            }
            return FormValidation.ok();
        }

        @POST
        public FormValidation doCheckRsfDomain(@QueryParameter String rsfDomain) throws IOException, ServletException {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            rsfDomain = Util.fixEmptyAndTrim(rsfDomain);
            if (rsfDomain != null) {
                try {
                    new URL("http://" + rsfDomain).openConnection().connect();
                } catch (Exception err) {
                    return FormValidation.error(err,
                            Messages.QiniuArtifactManagerFactory_DescriptorImpl_errors_invalidRsfDomain());
                }
            }
            return FormValidation.ok();
        }

        @POST
        public FormValidation doCheckUcDomain(@QueryParameter String ucDomain) throws IOException, ServletException {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            ucDomain = Util.fixEmptyAndTrim(ucDomain);
            if (ucDomain != null) {
                try {
                    new URL("http://" + ucDomain).openConnection().connect();
                } catch (Exception err) {
                    return FormValidation.error(err,
                            Messages.QiniuArtifactManagerFactory_DescriptorImpl_errors_invalidUcDomain());
                }
            }
            return FormValidation.ok();
        }

        @POST
        public FormValidation doCheckApiDomain(@QueryParameter String apiDomain) throws IOException, ServletException {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            apiDomain = Util.fixEmptyAndTrim(apiDomain);
            if (apiDomain != null) {
                try {
                    new URL("http://" + apiDomain).openConnection().connect();
                } catch (Exception err) {
                    return FormValidation.error(err,
                            Messages.QiniuArtifactManagerFactory_DescriptorImpl_errors_invalidAPIDomain());
                }
            }
            return FormValidation.ok();
        }

        @POST
        public FormValidation doCheckMultipartUploadConcurrency(@QueryParameter String multipartUploadConcurrency)
                throws IOException, ServletException {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            multipartUploadConcurrency = Util.fixEmptyAndTrim(multipartUploadConcurrency);
            if (multipartUploadConcurrency != null) {
                try {
                    int num = Integer.parseInt(multipartUploadConcurrency);
                    if (num <= 0) {
                        throw new NumberFormatException("multipartUploadConcurrency must be positive");
                    }
                } catch (NumberFormatException err) {
                    return FormValidation.error(err,
                            Messages.QiniuArtifactManagerFactory_DescriptorImpl_errors_invalidMultipartUploadConcurrency());
                }
            }
            return FormValidation.ok();
        }

        @POST
        public FormValidation doCheckMultipartUploadPartSize(@QueryParameter String multipartUploadPartSize)
                throws IOException, ServletException {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            multipartUploadPartSize = Util.fixEmptyAndTrim(multipartUploadPartSize);
            if (multipartUploadPartSize != null) {
                try {
                    int num = Integer.parseInt(multipartUploadPartSize);
                    if (num <= 0) {
                        throw new NumberFormatException("multipartUploadPartSize must be positive");
                    }
                } catch (NumberFormatException err) {
                    return FormValidation.error(err,
                            Messages.QiniuArtifactManagerFactory_DescriptorImpl_errors_invalidMultipartUploadPartSize());
                }
            }
            return FormValidation.ok();
        }

        @POST
        public FormValidation doCheckMultipartUploadThreshold(@QueryParameter String multipartUploadThreshold)
                throws IOException, ServletException {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            multipartUploadThreshold = Util.fixEmptyAndTrim(multipartUploadThreshold);
            if (multipartUploadThreshold != null) {
                try {
                    int num = Integer.parseInt(multipartUploadThreshold);
                    if (num <= 0) {
                        throw new NumberFormatException("multipartUploadThreshold must be positive");
                    }
                } catch (NumberFormatException err) {
                    return FormValidation.error(err,
                            Messages.QiniuArtifactManagerFactory_DescriptorImpl_errors_invalidMultipartUploadThreshold());
                }
            }
            return FormValidation.ok();
        }

        @POST
        public FormValidation doCheckConnectTimeout(@QueryParameter String connectTimeout)
                throws IOException, ServletException {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            connectTimeout = Util.fixEmptyAndTrim(connectTimeout);
            if (connectTimeout != null) {
                try {
                    int num = Integer.parseInt(connectTimeout);
                    if (num <= 0) {
                        throw new NumberFormatException("connectTimeout must be positive");
                    }
                } catch (NumberFormatException err) {
                    return FormValidation.error(err,
                            Messages.QiniuArtifactManagerFactory_DescriptorImpl_errors_invalidConnectTimeout());
                }
            }
            return FormValidation.ok();
        }

        @POST
        public FormValidation doCheckReadTimeout(@QueryParameter String readTimeout)
                throws IOException, ServletException {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            readTimeout = Util.fixEmptyAndTrim(readTimeout);
            if (readTimeout != null) {
                try {
                    int num = Integer.parseInt(readTimeout);
                    if (num <= 0) {
                        throw new NumberFormatException("readTimeout must be positive");
                    }
                } catch (NumberFormatException err) {
                    return FormValidation.error(err,
                            Messages.QiniuArtifactManagerFactory_DescriptorImpl_errors_invalidReadTimeout());
                }
            }
            return FormValidation.ok();
        }

        @POST
        public FormValidation doCheckWriteTimeout(@QueryParameter String writeTimeout)
                throws IOException, ServletException {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            writeTimeout = Util.fixEmptyAndTrim(writeTimeout);
            if (writeTimeout != null) {
                try {
                    int num = Integer.parseInt(writeTimeout);
                    if (num <= 0) {
                        throw new NumberFormatException("writeTimeout must be positive");
                    }
                } catch (NumberFormatException err) {
                    return FormValidation.error(err,
                            Messages.QiniuArtifactManagerFactory_DescriptorImpl_errors_invalidWriteTimeout());
                }
            }
            return FormValidation.ok();
        }

        @POST
        public FormValidation doCheckRetryCount(@QueryParameter String retryCount)
                throws IOException, ServletException {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            retryCount = Util.fixEmptyAndTrim(retryCount);
            if (retryCount != null) {
                try {
                    int num = Integer.parseInt(retryCount);
                    if (num <= 0) {
                        throw new NumberFormatException("retryCount must be positive");
                    }
                } catch (NumberFormatException err) {
                    return FormValidation.error(err,
                            Messages.QiniuArtifactManagerFactory_DescriptorImpl_errors_invalidRetryCount());
                }
            }
            return FormValidation.ok();
        }

        private Throwable checkAccessKeySecretKeyAndBucketName(final String accessKey, final Secret secretKey,
                final String bucketName, final String upDomain, final String rsDomain, final String rsfDomain,
                final String ucDomain, final String apiDomain, final boolean useHTTPs) {
            final BucketManager bucketManager = this.getBucketManager(accessKey, secretKey, upDomain, rsDomain,
                    rsfDomain, ucDomain, apiDomain, useHTTPs);
            if (bucketManager != null && bucketName != null) {
                try {
                    bucketManager.getBucketInfo(bucketName);
                } catch (QiniuException e) {
                    return e;
                }
            }
            return null;
        }

        private boolean checkDownloadDomain(final String accessKey, final Secret secretKey, final String bucketName,
                final String downloadDomain, final String upDomain, final String rsDomain, final String rsfDomain,
                final String ucDomain, final String apiDomain, final boolean useHTTPs) {
            final BucketManager bucketManager = this.getBucketManager(accessKey, secretKey, upDomain, rsDomain,
                    rsfDomain, ucDomain, apiDomain, useHTTPs);
            if (bucketManager != null && bucketName != null && downloadDomain == null) {
                try {
                    final String defaultIoSrcHost = bucketManager.getDefaultIoSrcHost(bucketName);
                    if (defaultIoSrcHost != null && !defaultIoSrcHost.isEmpty()) {
                        return true;
                    }
                } catch (QiniuException e) {
                    // do nothing
                }
                try {
                    final String[] domainList = bucketManager.domainList(bucketName);
                    return domainList.length > 0;
                } catch (QiniuException e) {
                    return false;
                }
            }
            return true;
        }

        @CheckForNull
        private BucketManager getBucketManager(final String accessKey, final Secret secretKey, final String upDomain,
                final String rsDomain, final String rsfDomain, final String ucDomain, final String apiDomain,
                final boolean useHTTPs) {
            Initializer.setAppName();

            final Auth auth = this.getAuth(accessKey, secretKey);
            if (auth == null) {
                return null;
            }
            final Configuration config = this.getConfiguration(upDomain, rsDomain, rsfDomain, ucDomain, apiDomain,
                    useHTTPs);
            return new BucketManager(auth, config);
        }

        @CheckForNull
        private Auth getAuth(final String accessKey, final Secret secretKey) {
            String secretKeyPlainText = null;

            if (secretKey != null) {
                secretKeyPlainText = Util.fixEmptyAndTrim(secretKey.getPlainText());
            }
            if (accessKey == null || secretKeyPlainText == null) {
                return null;
            }
            return Auth.create(accessKey, secretKeyPlainText);
        }

        @Nonnull
        private Configuration getConfiguration(String upDomain, String rsDomain, String rsfDomain, String ucDomain,
                String apiDomain, final boolean useHTTPs) {
            rsDomain = Util.fixEmptyAndTrim(rsDomain);
            ucDomain = Util.fixEmptyAndTrim(ucDomain);
            apiDomain = Util.fixEmptyAndTrim(apiDomain);
            upDomain = Util.fixEmptyAndTrim(upDomain);
            rsfDomain = Util.fixEmptyAndTrim(rsfDomain);

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
            config.useHttpsDomains = useHTTPs;
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
    }

    public static class QiniuRuntimeException extends RuntimeException {
        public QiniuRuntimeException(final Throwable cause) {
            super(cause);
        }
    }

    public static class CannotGetDownloadDomain extends IllegalArgumentException {
        public CannotGetDownloadDomain(final String s) {
            super(s);
        }
    }

    @Nonnull
    public BucketManager getBucketManager() {
        return this.config.getBucketManager();
    }

    @Nonnull
    public Auth getAuth() {
        return this.config.getAuth();
    }

    @Nonnull
    public Configuration getConfiguration() {
        return this.config.getConfiguration();
    }

    @Nonnull
    public String getAccessKey() {
        return this.config.getAccessKey();
    }

    @Nonnull
    public Secret getSecretKey() {
        return this.config.getSecretKey();
    }

    @Nonnull
    public String getBucketName() {
        return this.config.getBucketName();
    }

    @Nonnull
    public String getObjectNamePrefix() {
        return this.config.getObjectNamePrefix();
    }

    @Nonnull
    public String getDownloadDomain() {
        return this.config.getDownloadDomain();
    }

    @Nonnull
    public String getUpDomain() {
        return this.config.getUpDomain();
    }

    @Nonnull
    public String getRsDomain() {
        return this.config.getRsDomain();
    }

    @Nonnull
    public String getRsfDomain() {
        return this.config.getRsfDomain();
    }

    @Nonnull
    public String getUcDomain() {
        return this.config.getUcDomain();
    }

    @Nonnull
    public String getApiDomain() {
        return this.config.getApiDomain();
    }

    public boolean isUseHTTPs() {
        return this.config.isUseHTTPs();
    }

    public boolean isInfrequentStorage() {
        return this.config.isInfrequentStorage();
    }

    public boolean isDeleteArtifacts() {
        return this.config.isDeleteArtifacts();
    }

    public boolean isApplyForAllJobs() {
        return this.config.isApplyForAllJobs();
    }

    public int getMultipartUploadConcurrency() {
        return this.config.getMultipartUploadConcurrency();
    }

    public int getMultipartUploadPartSize() {
        return this.config.getMultipartUploadPartSize();
    }

    public int getMultipartUploadThreshold() {
        return this.config.getMultipartUploadThreshold();
    }

    public int getConnectTimeout() {
        return this.config.getConnectTimeout();
    }

    public int getReadTimeout() {
        return this.config.getReadTimeout();
    }

    public int getWriteTimeout() {
        return this.config.getWriteTimeout();
    }

    public int getRetryCount() {
        return this.config.getRetryCount();
    }
}
