package io.jenkins.plugins;

import com.qiniu.http.Client;

final class Initializer {
    private static boolean haveSet = false;

    static void setAppName() {
        if (!haveSet) {
            Client.setAppName("Qiniu-Jenkins-Plugin");
            haveSet = true;
        }
    }
}
