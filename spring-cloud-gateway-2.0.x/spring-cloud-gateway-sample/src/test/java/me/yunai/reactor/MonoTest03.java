package me.yunai.reactor;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.function.Function;

public class MonoTest03 {

    public static void main(String[] args) throws InterruptedException {
//        Mono.just("1")
//                .flatMap(s -> Mono.empty())
//                .subscribe(o -> System.out.println("结果：" + o));

        Flux.just(5, 10, 15)
                .concatMap(new Function<Integer, Publisher<?>>() {
                    @Override
                    public Publisher<?> apply(Integer number) {
                        if (number.equals(10)) {
                            return Mono.just(5);
                        }
                        if (number.equals(15)) {
                            return Mono.just(10);
                        }
                        return Mono.empty();
                    }
                })
                .next()
                .subscribe(System.out::println);

        Thread.sleep(10000L);
    }

}
