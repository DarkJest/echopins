package dev.echopins.infrastructure.network;

import net.minecraft.network.FriendlyByteBuf;

import java.util.function.BiConsumer;
import java.util.function.Function;

/** Minimal packet codec shared by the 1.20.1 loader networking APIs. */
public interface PacketCodec<T> {
    void encode(FriendlyByteBuf buffer, T value);

    T decode(FriendlyByteBuf buffer);

    static <T> PacketCodec<T> of(BiConsumer<FriendlyByteBuf, T> encoder,
                                 Function<FriendlyByteBuf, T> decoder) {
        return new PacketCodec<>() {
            @Override
            public void encode(FriendlyByteBuf buffer, T value) {
                encoder.accept(buffer, value);
            }

            @Override
            public T decode(FriendlyByteBuf buffer) {
                return decoder.apply(buffer);
            }
        };
    }

    static <T> PacketCodec<T> unit(T value) {
        return of((buffer, ignored) -> { }, buffer -> value);
    }
}
