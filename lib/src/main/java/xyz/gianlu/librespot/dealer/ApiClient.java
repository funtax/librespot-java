/*
 * Copyright 2021 devgianlu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package xyz.gianlu.librespot.dealer;

import com.google.protobuf.Message;
import com.spotify.connectstate.Connect;
import com.spotify.extendedmetadata.ExtendedMetadata;
import com.spotify.metadata.Metadata;
import okhttp3.*;
import okio.BufferedSink;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.gianlu.librespot.core.ApResolver;
import xyz.gianlu.librespot.core.Session;
import xyz.gianlu.librespot.mercury.MercuryClient;
import xyz.gianlu.librespot.metadata.*;

import java.io.IOException;

import static com.spotify.canvaz.CanvazOuterClass.EntityCanvazRequest;
import static com.spotify.canvaz.CanvazOuterClass.EntityCanvazResponse;

/**
 * @author devgianlu
 */
public final class ApiClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiClient.class);
    private final Session session;
    private final String baseUrl;

    public ApiClient(@NotNull Session session) {
        this.session = session;
        this.baseUrl = "https://" + ApResolver.getRandomSpclient();
    }

    @NotNull
    public static RequestBody protoBody(@NotNull Message msg) {
        return new RequestBody() {
            @Override
            public MediaType contentType() {
                return MediaType.get("application/protobuf");
            }

            @Override
            public void writeTo(@NotNull BufferedSink sink) throws IOException {
                sink.write(msg.toByteArray());
            }
        };
    }

    @NotNull
    private Request buildRequest(@NotNull String method, @NotNull String suffix, @Nullable Headers headers, @Nullable RequestBody body) throws IOException, MercuryClient.MercuryException {
        Request.Builder request = new Request.Builder();
        request.method(method, body);
        if (headers != null) request.headers(headers);
        request.addHeader("Authorization", "Bearer " + session.tokens().get("playlist-read"));
        request.url(baseUrl + suffix);
        return request.build();
    }

    public void sendAsync(@NotNull String method, @NotNull String suffix, @Nullable Headers headers, @Nullable RequestBody body, @NotNull Callback callback) throws IOException, MercuryClient.MercuryException {
        session.client().newCall(buildRequest(method, suffix, headers, body)).enqueue(callback);
    }

    /**
     * Sends a request to the Spotify API.
     *
     * @param method  The request method
     * @param suffix  The suffix to be appended to {@link #baseUrl} also know as path
     * @param headers Additional headers
     * @param body    The request body
     * @param tries   How many times the request should be reattempted (0 = none)
     * @return The response
     * @throws IOException                    The last {@link IOException} thrown by {@link Call#execute()}
     * @throws MercuryClient.MercuryException If the API token couldn't be requested
     */
    @NotNull
    public Response send(@NotNull String method, @NotNull String suffix, @Nullable Headers headers, @Nullable RequestBody body, int tries) throws IOException, MercuryClient.MercuryException {
        IOException lastEx;
        do {
            try {
                Response resp = session.client().newCall(buildRequest(method, suffix, headers, body)).execute();
                if (resp.code() == 503) {
                    lastEx = new StatusCodeException(resp);
                    continue;
                }

                return resp;
            } catch (IOException ex) {
                lastEx = ex;
            }
        } while (tries-- > 1);

        throw lastEx;
    }

    @NotNull
    public Response send(@NotNull String method, @NotNull String suffix, @Nullable Headers headers, @Nullable RequestBody body) throws IOException, MercuryClient.MercuryException {
        return send(method, suffix, headers, body, 1);
    }

    public void putConnectState(@NotNull String connectionId, @NotNull Connect.PutStateRequest proto) throws IOException, MercuryClient.MercuryException {
        try (Response resp = send("PUT", "/connect-state/v1/devices/" + session.deviceId(), new Headers.Builder()
                .add("X-Spotify-Connection-Id", connectionId).build(), protoBody(proto), 5 /* We want this to succeed */)) {
            if (resp.code() == 413)
                LOGGER.warn("PUT state payload is too large: {} bytes uncompressed.", proto.getSerializedSize());
            else if (resp.code() != 200)
                LOGGER.warn("PUT state returned {}. {headers: {}}", resp.code(), resp.headers());
        }
    }

    @NotNull
    public Metadata.Track getMetadata4Track(@NotNull TrackId track) throws IOException, MercuryClient.MercuryException {
        try (Response resp = send("GET", "/metadata/4/track/" + track.hexId(), null, null)) {
            StatusCodeException.checkStatus(resp);

            ResponseBody body;
            if ((body = resp.body()) == null) throw new IOException();
            return Metadata.Track.parseFrom(body.byteStream());
        }
    }

    @NotNull
    public Metadata.Episode getMetadata4Episode(@NotNull EpisodeId episode) throws IOException, MercuryClient.MercuryException {
        try (Response resp = send("GET", "/metadata/4/episode/" + episode.hexId(), null, null)) {
            StatusCodeException.checkStatus(resp);

            ResponseBody body;
            if ((body = resp.body()) == null) throw new IOException();
            return Metadata.Episode.parseFrom(body.byteStream());
        }
    }

    @NotNull
    public Metadata.Album getMetadata4Album(@NotNull AlbumId album) throws IOException, MercuryClient.MercuryException {
        try (Response resp = send("GET", "/metadata/4/album/" + album.hexId(), null, null)) {
            StatusCodeException.checkStatus(resp);

            ResponseBody body;
            if ((body = resp.body()) == null) throw new IOException();
            return Metadata.Album.parseFrom(body.byteStream());
        }
    }

    @NotNull
    public Metadata.Artist getMetadata4Artist(@NotNull ArtistId artist) throws IOException, MercuryClient.MercuryException {
        try (Response resp = send("GET", "/metadata/4/artist/" + artist.hexId(), null, null)) {
            StatusCodeException.checkStatus(resp);

            ResponseBody body;
            if ((body = resp.body()) == null) throw new IOException();
            return Metadata.Artist.parseFrom(body.byteStream());
        }
    }

    @NotNull
    public Metadata.Show getMetadata4Show(@NotNull ShowId show) throws IOException, MercuryClient.MercuryException {
        try (Response resp = send("GET", "/metadata/4/show/" + show.hexId(), null, null)) {
            StatusCodeException.checkStatus(resp);

            ResponseBody body;
            if ((body = resp.body()) == null) throw new IOException();
            return Metadata.Show.parseFrom(body.byteStream());
        }
    }

    @NotNull
    public EntityCanvazResponse getCanvases(@NotNull EntityCanvazRequest req) throws IOException, MercuryClient.MercuryException {
        try (Response resp = send("POST", "/canvaz-cache/v0/canvases", null, protoBody(req))) {
            StatusCodeException.checkStatus(resp);

            ResponseBody body;
            if ((body = resp.body()) == null) throw new IOException();
            return EntityCanvazResponse.parseFrom(body.byteStream());
        }
    }

    public ExtendedMetadata.BatchedExtensionResponse getExtendedMetadata(@NotNull ExtendedMetadata.BatchedEntityRequest req) throws IOException, MercuryClient.MercuryException {
        try (Response resp = send("POST", "/extended-metadata/v0/extended-metadata", null, protoBody(req))) {
            StatusCodeException.checkStatus(resp);

            ResponseBody body;
            if ((body = resp.body()) == null) throw new IOException();
            return ExtendedMetadata.BatchedExtensionResponse.parseFrom(body.byteStream());
        }
    }

    public static class StatusCodeException extends IOException {
        public final int code;

        StatusCodeException(@NotNull Response resp) {
            super(String.format("%d: %s", resp.code(), resp.message()));
            code = resp.code();
        }

        private static void checkStatus(@NotNull Response resp) throws StatusCodeException {
            if (resp.code() != 200) throw new StatusCodeException(resp);
        }
    }
}
