package me.yunai.reactor;

import reactor.core.publisher.Flux;

import java.util.Arrays;
import java.util.List;

public class MonoTest01 {

    public static void main(String[] args) {
//        Flux.just("1", "2").next().doOnNext(new Consumer<String>() {
//            @Override
//            public void accept(String s) {
//                System.out.println("====s: " + s);
//            }
//        }).block();

//        Flux.just("1", "2").map(new Function<String, String>() {
//            @Override
//            public String apply(String s) {
//                return "3";
//            }
//        }).doOnNext(new Consumer<String>() {
//            @Override
//            public void accept(String s) {
//                System.out.println("====s: " + s);
//            }
//        }).blockLast();

//        Flux.just("1", "2").map(new Function<String, String>() {
//            @Override
//            public String apply(String s) {
//                return "3";
//            }
//        }).doOnNext(new Consumer<String>() {
//            @Override
//            public void accept(String s) {
//                System.out.println("====s: " + s);
//            }
//        }).then().doOnNext(new Consumer<Void>() {
//            @Override
//            public void accept(Void aVoid) {
//                System.out.println("====x:");
//            }
//        }).block();

//        System.out.println(Thread.currentThread());
//        Flux.just("1", "2").map(new Function<String, String>() {
//            @Override
//            public String apply(String s) {
//                return "3";
//            }
//        }).doOnNext(new Consumer<String>() {
//            @Override
//            public void accept(String s) {
//                System.out.println(Thread.currentThread() + "====s: " + s);
//            }
//        }).then(Mono.defer(new Supplier<Mono<String>>() {
//            @Override
//            public Mono<String> get() {
//                return Mono.just("1");
//            }
//        })).doOnNext(new Consumer<String>() {
//
//            @Override
//            public void accept(String s) {
//                System.out.println(Thread.currentThread() + "====x: " + s);
//            }
//        }).block();

        List<String> words = Arrays.asList(
                "the",
                "quick",
                "brown",
                "fox",
                "jumped",
                "over",
                "the",
                "lazy",
                "dog",
                "dog"
        );
        Flux<String> manyLetters = Flux
                .fromIterable(words)
                .flatMap(word -> Flux.fromArray(word.split("")))
                .distinct()
                .sort()
                .zipWith(Flux.range(1, Integer.MAX_VALUE),
                        (string, count) -> String.format("%2d. %s", count, string));

        manyLetters.subscribe(System.out::println);
    }

}
