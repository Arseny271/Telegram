package org.telegram.messenger.chromecast;

import androidx.annotation.Nullable;

class ChromecastControllerState {
    private ChromecastFileServer server;
    private ChromecastMediaVariations media;
    private ChromecastController.RemoteMediaClientHandler client;

    public void setMedia(ChromecastMediaVariations m) {
        if (client != null && m != null) {
            addToFileServer(m);
        }

        if (client != null && media != null) {
            removeFromFileServer(media);
        }

        if (client != null && m != null) {
            client.load(m);
        }

        media = m;
    }

    @Nullable
    public ChromecastMediaVariations getMedia() {
        return media;
    }

    public void setClient(ChromecastController.RemoteMediaClientHandler c) {
        if (media != null && client == null && c != null) {
            addToFileServer(media);
        }

        if (client != null && media != null && c == null) {
            removeFromFileServer(media);
        }

        if (client != null) {
            client.unregister();
        }

        if (c != null) {
            c.register();

            if (media != null) {
                c.load(media);
            }
        }

        client = c;
    }

    @Nullable
    public ChromecastController.RemoteMediaClientHandler getClient() {
        return client;
    }

    private void addToFileServer(ChromecastMediaVariations media) {
        if (server == null) {
            server = new ChromecastFileServer();
        }

        for (int a = 0; a < media.getVariationsCount(); a++) {
            server.addFileToCast(media.getVariation(a));
        }
    }

    private void removeFromFileServer(ChromecastMediaVariations media) {
        if (server == null) {
            return;
        }

        for (int a = 0; a < media.getVariationsCount(); a++) {
            server.removeFileFromCast(media.getVariation(a));
        }
    }
}
