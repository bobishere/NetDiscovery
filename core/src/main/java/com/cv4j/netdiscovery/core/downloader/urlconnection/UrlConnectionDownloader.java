package com.cv4j.netdiscovery.core.downloader.urlconnection;

import com.cv4j.netdiscovery.core.cache.RxCacheManager;
import com.cv4j.netdiscovery.core.config.Constant;
import com.cv4j.netdiscovery.core.cookies.CookiesPool;
import com.cv4j.netdiscovery.core.domain.Request;
import com.cv4j.netdiscovery.core.domain.Response;
import com.cv4j.netdiscovery.core.downloader.Downloader;
import com.safframework.rxcache.domain.Record;
import com.safframework.tony.common.utils.IOUtils;
import com.safframework.tony.common.utils.Preconditions;
import io.reactivex.Maybe;
import io.reactivex.MaybeEmitter;
import io.reactivex.MaybeOnSubscribe;
import io.reactivex.functions.Function;
import io.vertx.core.http.HttpMethod;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;

/**
 * Created by tony on 2018/3/2.
 */
@Slf4j
public class UrlConnectionDownloader implements Downloader {

    private URL url = null;
    private HttpURLConnection httpUrlConnection = null;

    public UrlConnectionDownloader() {
    }

    @Override
    public Maybe<Response> download(Request request) {

        if (request.isDebug()) { // request 在 debug 模式下，并且缓存中包含了数据，则使用缓存中的数据

            if (RxCacheManager.getInsatance().getRxCache()!=null
                    && RxCacheManager.getInsatance().getRxCache().get(request.getUrl(),Response.class)!=null) {

                Record<Response> response = RxCacheManager.getInsatance().getRxCache().get(request.getUrl(),Response.class);
                return Maybe.just(response.getData());
            }
        }

        try {
            url = new URL(request.getUrl());

            // 设置Proxy
            if (request.getProxy()!=null) {

                httpUrlConnection = (HttpURLConnection) url.openConnection(request.getProxy().toJavaNetProxy());
            } else {

                httpUrlConnection = (HttpURLConnection) url.openConnection();
            }

            // 使用Post请求时，设置Post body
            if (request.getHttpMethod() == HttpMethod.POST) {

                httpUrlConnection.setDoOutput(true);
                httpUrlConnection.setDoInput(true);
                httpUrlConnection.setRequestMethod("POST");
                httpUrlConnection.setUseCaches(false); // post 请求不用缓存

                if (request.getHttpRequestBody()!=null) {

                    httpUrlConnection.setRequestProperty(Constant.CONTENT_TYPE, request.getHttpRequestBody().getContentType());

                    OutputStream os = httpUrlConnection.getOutputStream();
                    os.write(request.getHttpRequestBody().getBody());
                    os.flush();
                    os.close();
                }
            }

            //设置请求头header
            if (Preconditions.isNotBlank(request.getHeader())) {

                for (Map.Entry<String, String> entry:request.getHeader().entrySet()) {
                    httpUrlConnection.setRequestProperty(entry.getKey(),entry.getValue());
                }
            }

            //设置字符集
            if (Preconditions.isNotBlank(request.getCharset())) {

                httpUrlConnection.setRequestProperty("Accept-Charset", request.getCharset());
            }

            httpUrlConnection.connect();

           return Maybe.create(new MaybeOnSubscribe<InputStream>() {

                @Override
                public void subscribe(MaybeEmitter<InputStream> emitter) throws Exception {

                    emitter.onSuccess(httpUrlConnection.getInputStream());
                }
            }).map(new Function<InputStream, Response>() {

                @Override
                public Response apply(InputStream inputStream) throws Exception {

                    Response response = new Response();
                    response.setContent(IOUtils.readInputStream(inputStream));
                    response.setStatusCode(httpUrlConnection.getResponseCode());
                    response.setContentType(httpUrlConnection.getContentType());

                    if (request.isSaveCookie()) {

                        // save cookies
                        Map<String, List<String>> maps = httpUrlConnection.getHeaderFields();
                        List<String> cookies = maps.get(Constant.SET_COOKIES_HEADER);
                        CookiesPool.getInsatance().saveCookie(request,cookies);
                    }

                    if (request.isDebug()) { // request 在 debug 模式，则缓存response

                        save(request.getUrl(),response);
                    }

                    return response;
                }
            });

        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return Maybe.empty();
    }

    @Override
    public void close() throws IOException {

        if (httpUrlConnection!=null) {

            httpUrlConnection.disconnect();
        }
    }
}
