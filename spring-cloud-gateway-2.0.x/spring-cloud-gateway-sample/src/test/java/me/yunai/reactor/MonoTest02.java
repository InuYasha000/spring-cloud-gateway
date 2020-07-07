package me.yunai.reactor;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.function.Consumer;
import java.util.function.Function;

public class MonoTest02 {

    public static void main(String[] args) throws InterruptedException {
//        Mono.just("1").map(new Function<String, Mono<Void>>() {
//            @Override
//            public Mono<Void> apply(String s) {
//                return Mono.<Void>empty();
//            }
//        }).subscribe()

        Mono.just("1").publishOn(Schedulers.newParallel("io")).flatMap(new Function<String, Mono<String>>() {
            @Override
            public Mono<String> apply(String s) {
                System.out.println(Thread.currentThread());
                return Mono.just("3");
            }
        }).then(Mono.just("2")).subscribe(new Consumer<String>() {
            @Override
            public void accept(String s) {
                System.out.println(Thread.currentThread());
                System.out.println(s);
            }
        });

//        Thread.sleep(1000L);
    }

}
