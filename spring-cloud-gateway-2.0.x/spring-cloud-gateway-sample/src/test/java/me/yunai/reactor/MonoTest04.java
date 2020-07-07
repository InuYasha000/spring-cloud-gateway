package me.yunai.reactor;

import reactor.core.publisher.Mono;

import java.util.function.Consumer;

public class MonoTest04 {

    public static void main(String[] args) throws InterruptedException {
         Mono<String> s1 = Mono.just("123").doOnNext(new Consumer<String>() {
            @Override
            public void accept(String s) {
                System.out.println("123:sub");
            }
        });
        Mono<String> s2 = Mono.just("246").doOnNext(new Consumer<String>() {
            @Override
            public void accept(String s) {
                System.out.println("246:sub");
            }
        });
        Mono s3 = Mono.just("357").then().doOnNext(new Consumer<Void>() {
            @Override
            public void accept(Void aVoid) {
                System.out.println("357:sub");
            }
        });

        String s4 = Mono.just("666").doOnNext(new Consumer<String>() {
            @Override
            public void accept(String s) {
                System.out.println(s);
            }
        }).block();

        Mono.when(s1, s2, s3).then().doOnNext(new Consumer<Void>() {
            @Override
            public void accept(Void aVoid) {
                System.out.println("123312");
            }
        }).then().block();
        Thread.sleep(10000);
    }

}
