package me.yunai.reactor;

import reactor.core.publisher.Flux;

import java.util.function.BiFunction;
import java.util.function.Function;

public class MonoTest08 {

    public static void main(String[] args) {
        Flux.just(1, 2, 3)
                .map(new Function<Integer, Integer>() {
                    @Override
                    public Integer apply(Integer integer) {
                        System.out.println(integer);
                        return integer;
                    }
                }).blockLast();

        Flux.just(1, 2, 3)
                .reduce(new BiFunction<Integer, Integer, Integer>() {
                    @Override
                    public Integer apply(Integer integer, Integer integer2) {
                        return integer + integer2;
                    }
                })
                .map(new Function<Integer, Integer>() {
                    @Override
                    public Integer apply(Integer integer) {
                        System.out.println(integer);
                        return integer;
                    }
                }).block();
    }

}
