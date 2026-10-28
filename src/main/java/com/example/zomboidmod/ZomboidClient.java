package com.example.zomboidmod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;

public class ZomboidClient implements ClientModInitializer {
    private static int thirst = 100;
    private static int fatigue = 100;
    private static int weight = 0;
    private static int capacity = StatsManager.CAPACITY;
    private static boolean bleeding = false;
    private static boolean infected = false;

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(StatsPayload.ID, (payload, context) -> {
            thirst = payload.thirst();
            fatigue = payload.fatigue();
            bleeding = payload.bleeding();
            infected = payload.infected();
            weight = payload.weight();
            capacity = payload.capacity();
        });

        HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
            MinecraftClient c = MinecraftClient.getInstance();
            if (c.options.hudHidden || c.player == null) return;
            int x = 8, y = 8;
            ctx.drawTextWithShadow(c.textRenderer, "Жажда: " + thirst + "%", x, y, color(thirst));
            ctx.drawTextWithShadow(c.textRenderer, "Бодрость: " + fatigue + "%", x, y + 11, color(fatigue));
            int cap = Math.max(1, capacity);
            ctx.drawTextWithShadow(c.textRenderer, "Вес: " + weight + "/" + cap, x, y + 22,
                    color(Math.max(0, 100 - weight * 100 / cap)));
            int line = 33;
            if (bleeding) {
                ctx.drawTextWithShadow(c.textRenderer, "Кровотечение!", x, y + line, 0xFF5555);
                line += 11;
            }
            if (infected) {
                ctx.drawTextWithShadow(c.textRenderer, "Заражение (укус)!", x, y + line, 0xCC55FF);
            }
        });
    }

    private static int color(int v) {
        if (v > 50) return 0x55FF55;
        if (v > 20) return 0xFFFF55;
        return 0xFF5555;
    }
}
