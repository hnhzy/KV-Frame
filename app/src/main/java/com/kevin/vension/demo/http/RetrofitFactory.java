package com.kevin.vension.demo.http;

import android.content.Context;
import android.util.Log;

import com.google.gson.GsonBuilder;
import com.kevin.vension.demo.models.TokenEntity;
import com.kevin.vension.demo.utils.UserACache;
import com.vension.frame.VFrame;
import com.vension.frame.dialogs.VLoadingDialog;
import com.vension.frame.utils.VLogUtil;

import java.io.File;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;
import okhttp3.Cache;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.converter.scalars.ScalarsConverterFactory;

/**
 * Created by Vension on 2017/9/7.
 * Retrofit网络工厂
 */

public class RetrofitFactory {

	private static RetrofitFactory _RetrofitFactory = null;
	private static ApiService _ApiService = null;

	static public RetrofitFactory getInstance() {
		if (_RetrofitFactory == null) {
			synchronized (RetrofitFactory.class) {
				if (_RetrofitFactory == null)
					_RetrofitFactory = new RetrofitFactory();
			}
		}
		return _RetrofitFactory;
	}

	private RetrofitFactory() {
		initOkHttp();
	}

	public static ApiService getApiService() {
		return _ApiService;
	}



	/**
	 * 初始化网络通信服务
	 */
	public void initOkHttp() {
		// 指定缓存路径,缓存大小100Mb
		//cache url
		Cache cache = new Cache(new File(VFrame.getContext().getCacheDir(), "HttpCache"), 1024 * 1024 * 100);

		OkHttpClient okHttpClient = new OkHttpClient.Builder()
				.addInterceptor(InterceptorUtil.HeaderParamInterceptor())//自定义的拦截器，用于添加公共参数
				.addInterceptor(InterceptorUtil.initLogInterceptor())//拦截器，用于日志的打印
				.addNetworkInterceptor(InterceptorUtil.CacheInterceptor())//缓存拦截器
				.cache(cache)//设置缓存
				.retryOnConnectionFailure(true)//错误重连
				.connectTimeout(20, TimeUnit.SECONDS)//设置超时
				.readTimeout(20, TimeUnit.SECONDS)
				.writeTimeout(20, TimeUnit.SECONDS)
				.connectionPool(new ConnectionPool())
				.build();

		Retrofit retrofit = new Retrofit.Builder()
				.baseUrl(Api.getHostUrl())//设置BaseUrl
				.addConverterFactory(GsonConverterFactory.create(new GsonBuilder().create()))// 增加返回值为Gson实体的支持，不会进行解密等处理
				.addCallAdapterFactory(RxJava2CallAdapterFactory.create())// //Retrofit2与Rxjava2之间结合的适配器
				.addConverterFactory(ScalarsConverterFactory.create())// 增加返回值为String的支持
				.client(okHttpClient)
				.build();
		//获取Api服务
		_ApiService = retrofit.create(ApiService.class);
	}



//==============================================================================

	/**
	 * 回调
	 * AndroidSchedulers.mainThread() 主线程
	 * Schedulers.immediate() 当前线程，即默认Scheduler
	 * Schedulers.newThread() 启用新线程
	 * Schedulers.io() IO线程，内部是一个数量无上限的线程池，可以进行文件、数据库和网络操作。
	 * Schedulers.computation() CPU计算用的线程，内部是一个数目固定为CPU核数的线程池，适合于CPU密集型计算，不能操作文件、数据库和网络。
	 * *****************************************************************************************************
	 */
	protected final synchronized void handlerModelReq(Observable observable, HttpCallBack<?> callBack) {
		observable
				.subscribeOn(Schedulers.io())//请求数据的事件发生在io线程
				.subscribeOn(Schedulers.newThread())//子线程访问网络
				.observeOn(AndroidSchedulers.mainThread())//请求完成后在主线程更显UI
				.unsubscribeOn(Schedulers.io())//指定 Observer(观察者)所运行在的线程，或者叫做事件消费的线程
				.debounce(1, TimeUnit.SECONDS)
				.doOnNext(new OnNextConsumer())
				.doFinally(new onFinally())
				.retry(3)
				.subscribe(callBack);
	}

	protected final synchronized void handlerVersionReq(Observable observable, VersionCallBack<?> imple) {
		observable
				.subscribeOn(Schedulers.io())
				.observeOn(AndroidSchedulers.mainThread())
				.debounce(1, TimeUnit.SECONDS)
				.unsubscribeOn(Schedulers.io())
				.onErrorResumeNext(Observable.empty())
				.doOnNext(new OnNextConsumer())
				.doFinally(new onFinally())
				.subscribe(imple);
	}


	protected class onFinally implements Action {

		@Override
		public void run() throws Exception {
			Log.i("onFinally", "onFinally=");
			VLoadingDialog.with(VFrame.getContext()).dismiss();
		}
	}


	protected final class OnNextConsumer implements Consumer {

		@Override
		public void accept(@io.reactivex.annotations.NonNull Object result) throws Exception {
			if (MyHttpResponse.class.isInstance(result)) {

				switch (((MyHttpResponse) result).getCode()) {
					//token失效的处理
					case Api.Code.TOKEN_INVALID:
						VLogUtil.e("Authorization token失效的处理==》");
						getToken(new HttpCallBack<TokenEntity>() {

						@Override
						protected void onFinish() {

						}

						@Override
						protected void onFailure(String errorMessage) {

						}

						@Override
						protected void onSuccess(TokenEntity tokenEntity) {
							UserACache.getInstance().setToken(tokenEntity);
						}
					});
						break;

					case Api.Code.LOGIN_INVALID: {
						//登录状态的失效的处理
						VLogUtil.e("Authorization 登录状态的失效的处理==》");
						UserACache.getInstance().resetLogin();
					}
					break;
				}
			}
		}
	}


	/**
	 * 带对话框的网络请求
	 */
	public RetrofitFactory buildDialog(Context mContext) {
		VLoadingDialog.with(mContext).setCanceled(false).show();
		return this;
	}


	/** ================================== 以下是请求业务 ======================================*/


    // 获取Token
	public void getToken(HttpCallBack<?> callback){
//		handlerModelReq(_ApiService.getToken(new ReqToken("f3d259ddd3ed8ff3843839b","4c7f6f8fa93d59c45502c0ae8c4a95b","client_credentials")), callback);
	}

}