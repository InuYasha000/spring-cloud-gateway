package me.yunai.reactor;

import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;
import rx.Observable;
import rx.Subscription;
import rx.functions.Action0;
import rx.functions.Action1;

import java.util.function.Consumer;

public class MonoTest06 {

    public static void main(String[] args) {
        Mono.create(new Consumer<MonoSink< Object>>() {

            @Override
            public void accept(MonoSink<Object> s) {
                Subscription subscription = Observable.just("246").subscribe(new Action1<String>() {
                    @Override
                    public void call(String str) {
                        s.success(str);

                        System.out.println("onNext");
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        s.success();

                        System.out.println("onError");
                    }
                }, new Action0() {
                    @Override
                    public void call() {
                        s.success("sss");

                        System.out.println("onCompleted");

                    }
                });

//                Observable.just("123").subscribe(s::success, s::error, s::success);

//                s.onDispose(new Disposable() {
//                    @Override
//                    public void dispose() {
//                        System.out.println("#dispose");
//                    }
//                }).onRequest(new LongConsumer() {
//                    @Override
//                    public void accept(long value) {
//                        System.out.println("#accept");
//                    }
//                });

                s.onCancel(new Disposable() {
                    @Override
                    public void dispose() {
                        subscription.unsubscribe();
                    }
                });
            }
        }).doOnNext(new Consumer<Object>() {
            @Override
            public void accept(Object o) {
                System.out.println("6666");
            }
        }).doOnNext(new Consumer<Object>() {
            @Override
            public void accept(Object aVoid) {
                System.out.println("sb");
            }
        }).block();
    }

}
