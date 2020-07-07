package me.yunai.reactor;

import reactor.core.publisher.Flux;

import java.util.function.BiFunction;
import java.util.function.Consumer;

public class MonoTest07 {

    public static void main(String[] args) {
        Flux.just(1, 2, 3)
                .reduce(123, new BiFunction<Integer, Integer, Integer>() {
                    @Override
                    public Integer apply(Integer o, Integer integer) {
                        return o + integer;
                    }
                }).doOnNext(new Consumer<Integer>() {
            @Override
            public void accept(Integer integer) {
                System.out.println("result:" + integer);
            }
        }).block();
    }

}
