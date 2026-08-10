package dev.echopins.infrastructure.network;

import net.minecraft.resources.ResourceLocation;

/** A version-independent EchoPins packet identifier. */
public interface EchoPinsPayload {
    ResourceLocation id();
}
