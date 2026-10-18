import java.lang.instrument.Instrumentation;
import java.nio.file.*;
import java.util.function.Consumer;

/** Isolated audit client: camera settings and Minecraft's own framebuffer capture, no game assets. */
public final class ClientCaptureHarness {
    public static void premain(String directory, Instrumentation instrumentation) {
        Thread thread = new Thread(() -> run(Path.of(directory)), "MCCases visual audit"); thread.setDaemon(true); thread.start();
    }
    private static void run(Path directory) {
        try {
            Files.createDirectories(directory);
            Class<?> minecraftClass = Class.forName("net.minecraft.client.Minecraft");
            Object minecraft = null;
            while (minecraft == null) { Thread.sleep(200); minecraft = minecraftClass.getMethod("getInstance").invoke(null); }
            Object mc = minecraft;
            var execute = minecraftClass.getMethod("execute", Runnable.class);
            while (true) {
                Path command = directory.resolve("command.txt");
                if (!Files.exists(command)) { Thread.sleep(50); continue; }
                String text = Files.readString(command); Files.delete(command);
                execute.invoke(mc, (Runnable) () -> {
                    try {
                        Object options = minecraftClass.getField("options").get(mc);
                        options.getClass().getField("pauseOnLostFocus").setBoolean(options, false);
                        String[] args = text.strip().split("\\|", 2);
                        switch (args[0]) {
                            case "camera" -> {
                                Class<?> camera = Class.forName("net.minecraft.client.CameraType");
                                options.getClass().getMethod("setCameraType", camera).invoke(options, camera.getField(args[1]).get(null));
                            }
                            case "hand" -> {
                                Class<?> arm = Class.forName("net.minecraft.world.entity.HumanoidArm");
                                Object setting = options.getClass().getMethod("mainHand").invoke(options);
                                setting.getClass().getMethod("set", Object.class).invoke(setting, arm.getField(args[1]).get(null));
                                options.getClass().getMethod("broadcastOptions").invoke(options);
                            }
                            case "fov" -> {
                                Object setting = options.getClass().getMethod("fov").invoke(options);
                                setting.getClass().getMethod("set", Object.class).invoke(setting, Integer.valueOf(args[1]));
                            }
                            case "chat" -> {
                                Object setting = options.getClass().getMethod("chatVisibility").invoke(options);
                                Class<?> visibility = Class.forName("net.minecraft.world.entity.player.ChatVisiblity");
                                setting.getClass().getMethod("set", Object.class).invoke(setting, visibility.getField(args[1]).get(null));
                            }
                            case "command", "chattext" -> {
                                Object player = minecraftClass.getField("player").get(mc);
                                Object connection = player.getClass().getField("connection").get(player);
                                connection.getClass().getMethod(args[0].equals("command") ? "sendCommand" : "sendChat", String.class).invoke(connection, args[1]);
                            }
                            case "hover" -> {
                                Object window = minecraftClass.getMethod("getWindow").invoke(mc);
                                String[] xy = args[1].split(",");
                                double x = Double.parseDouble(xy[0]) * ((Number)window.getClass().getMethod("getScreenWidth").invoke(window)).doubleValue();
                                double y = Double.parseDouble(xy[1]) * ((Number)window.getClass().getMethod("getScreenHeight").invoke(window)).doubleValue();
                                Object mouse = minecraftClass.getField("mouseHandler").get(mc);
                                for (String axis : new String[]{"xpos", "ypos"}) { var f=mouse.getClass().getDeclaredField(axis);f.setAccessible(true);f.setDouble(mouse,axis.equals("xpos")?x:y); }
                            }
                            case "scroll" -> {
                                Object window = minecraftClass.getMethod("getWindow").invoke(mc);
                                long handle = ((Number) window.getClass().getMethod("handle").invoke(window)).longValue();
                                Object mouse = minecraftClass.getField("mouseHandler").get(mc);
                                mouse.getClass().getMethod("onScroll", long.class, double.class, double.class)
                                        .invoke(mouse, handle, 0d, Double.parseDouble(args[1]));
                            }
                            case "sneak" -> {
                                Object key = options.getClass().getField("keyShift").get(options);
                                key.getClass().getMethod("setDown", boolean.class).invoke(key, Boolean.parseBoolean(args[1]));
                            }
                            case "look" -> {
                                Object player = minecraftClass.getField("player").get(mc);
                                String[] angles = args[1].split(",");
                                player.getClass().getMethod("setYRot", float.class).invoke(player, Float.parseFloat(angles[0]));
                                player.getClass().getMethod("setXRot", float.class).invoke(player, Float.parseFloat(angles[1]));
                            }
                            case "use" -> {
                                var use = minecraftClass.getDeclaredMethod("startUseItem"); use.setAccessible(true); use.invoke(mc);
                            }
                            case "press" -> {
                                // One queued press of a vanilla key mapping, e.g. keySwapOffhand or keyAttack.
                                Object key = options.getClass().getField(args[1]).get(options);
                                var clicks = Class.forName("net.minecraft.client.KeyMapping").getDeclaredField("clickCount");
                                clicks.setAccessible(true); clicks.setInt(key, clicks.getInt(key) + 1);
                            }
                            case "hold" -> {
                                String[] key = args[1].split(",");
                                Object mapping = options.getClass().getField(key[0]).get(options);
                                mapping.getClass().getMethod("setDown", boolean.class).invoke(mapping, Boolean.parseBoolean(key[1]));
                            }
                            case "closescreen" -> {
                                // Close a menu like Escape does, so the server also knows the container is closed.
                                Object player = minecraftClass.getField("player").get(mc);
                                if (player != null) player.getClass().getMethod("closeContainer").invoke(player);
                                Object gui = minecraftClass.getField("gui").get(mc);
                                gui.getClass().getMethod("setScreen", Class.forName("net.minecraft.client.gui.screens.Screen")).invoke(gui, (Object) null);
                            }
                            case "select" -> {
                                Object player = minecraftClass.getField("player").get(mc);
                                Object inventory = player.getClass().getMethod("getInventory").invoke(player);
                                inventory.getClass().getMethod("setSelectedSlot", int.class).invoke(inventory, Integer.parseInt(args[1]));
                            }
                            case "reload" -> {
                                var future = (java.util.concurrent.CompletableFuture<?>) minecraftClass.getMethod("reloadResourcePacks").invoke(mc);
                                future.whenComplete((ignored, error) -> {
                                    try {
                                        if (error != null) Files.writeString(directory.resolve("error.txt"), error.toString());
                                        else Files.writeString(directory.resolve("done.txt"), text);
                                    } catch (Exception failure) { failure(directory, failure); }
                                });
                                return;
                            }
                            case "state" -> {
                                Object player = minecraftClass.getField("player").get(mc);
                                Object inventory = player.getClass().getMethod("getInventory").invoke(player);
                                String report = "slot=" + inventory.getClass().getMethod("getSelectedSlot").invoke(inventory) + "\n";
                                Path output = Path.of(args[1]); Files.createDirectories(output.getParent()); Files.writeString(output, report);
                            }
                            case "displays" -> {
                                Object level = minecraftClass.getField("level").get(mc);
                                Class<?> display = Class.forName("net.minecraft.world.entity.Display");
                                Class<?> interpolator = Class.forName("net.minecraft.world.entity.Display$GenericInterpolator");
                                var progressField = display.getDeclaredField("lastProgress"); progressField.setAccessible(true);
                                var targetMethod = display.getDeclaredMethod("createTransformation", Class.forName("net.minecraft.network.syncher.SynchedEntityData")); targetMethod.setAccessible(true);
                                StringBuilder report = new StringBuilder("id\ttype\ttick\tprogress\tx\tscale\ttargetX\ttargetScale\n");
                                for (Object entity : (Iterable<?>) level.getClass().getMethod("entitiesForRendering").invoke(level)) {
                                    if (!display.isInstance(entity)) continue;
                                    Object state = display.getMethod("renderState").invoke(entity); if (state == null) continue;
                                    float progress = progressField.getFloat(entity);
                                    Object curve = state.getClass().getMethod("transformation").invoke(state);
                                    Object pose = interpolator.getMethod("get", float.class).invoke(curve, progress);
                                    Object target = targetMethod.invoke(null, entity.getClass().getMethod("getEntityData").invoke(entity));
                                    report.append(entity.getClass().getMethod("getUUID").invoke(entity)).append('\t')
                                            .append(entity.getClass().getSimpleName()).append('\t')
                                            .append(entity.getClass().getField("tickCount").getInt(entity)).append('\t').append(progress);
                                    for (Object transform : new Object[]{pose, target}) for (String component : new String[]{"translation", "scale"}) {
                                        Object vector = transform.getClass().getMethod(component).invoke(transform);
                                        report.append('\t').append(Class.forName("org.joml.Vector3fc").getMethod("x").invoke(vector));
                                    }
                                    report.append('\n');
                                }
                                Path output = Path.of(args[1]); Files.createDirectories(output.getParent()); Files.writeString(output, report);
                            }
                            case "shot" -> {
                                Object renderer = minecraftClass.getField("gameRenderer").get(mc);
                                Object target = renderer.getClass().getMethod("mainRenderTarget").invoke(renderer);
                                Class<?> screenshot = Class.forName("net.minecraft.client.Screenshot");
                                Consumer<Object> consumer = nativeImage -> {
                                    try {
                                        Path output = Path.of(args[1]); Files.createDirectories(output.getParent());
                                        nativeImage.getClass().getMethod("writeToFile", Path.class).invoke(nativeImage, output);
                                        nativeImage.getClass().getMethod("close").invoke(nativeImage);
                                        Files.writeString(directory.resolve("done.txt"), text);
                                    } catch (Exception error) { failure(directory, error); }
                                };
                                screenshot.getMethod("takeScreenshot", Class.forName("com.mojang.blaze3d.pipeline.RenderTarget"), int.class, Consumer.class).invoke(null, target, 2, consumer);
                                return;
                            }
                            case "trace" -> {
                                // Samples, about every 10 ms for the given time, where the camera and the scenes
                                // riding the player are: scene = rider (interpolated) + its translation - camera.
                                String[] trace = args[1].split("\\|");
                                Path output = Path.of(trace[0]); Files.createDirectories(output.getParent());
                                long until = System.currentTimeMillis() + Long.parseLong(trace[1]);
                                StringBuffer report = new StringBuffer("ms\tpose\tcameraY\tplayerY\trider\triderY\ttransY\tsceneY\n");
                                Thread sampler = new Thread(() -> {
                                    try {
                                        while (System.currentTimeMillis() < until) {
                                            execute.invoke(mc, (Runnable) () -> {
                                                try { report.append(traceSample(minecraftClass, mc)); } catch (Exception error) { failure(directory, error); }
                                            });
                                            Thread.sleep(10);
                                        }
                                        Thread.sleep(50);
                                        Files.writeString(output, report.toString());
                                        Files.writeString(directory.resolve("done.txt"), text);
                                    } catch (Exception error) { failure(directory, error); }
                                }, "MCCases trace");
                                sampler.setDaemon(true); sampler.start();
                                return;
                            }
                            default -> throw new IllegalArgumentException("Unknown capture command " + text);
                        }
                        Files.writeString(directory.resolve("done.txt"), text);
                    } catch (Exception error) { failure(directory, error); }
                });
            }
        } catch (Exception error) { failure(directory, error); }
    }
    private static String traceSample(Class<?> minecraftClass, Object mc) throws Exception {
        Object renderer = minecraftClass.getField("gameRenderer").get(mc);
        Object camera = renderer.getClass().getMethod("mainCamera").invoke(renderer);
        Object position = camera.getClass().getMethod("position").invoke(camera);
        double cameraY = position.getClass().getField("y").getDouble(position);
        Object player = minecraftClass.getField("player").get(mc);
        Class<?> entity = Class.forName("net.minecraft.world.entity.Entity");
        Object tracker = minecraftClass.getMethod("getDeltaTracker").invoke(mc);
        float partial = (float) Class.forName("net.minecraft.client.DeltaTracker").getMethod("getGameTimeDeltaPartialTick", boolean.class)
                .invoke(tracker, false);
        Class<?> display = Class.forName("net.minecraft.world.entity.Display");
        Class<?> interpolator = Class.forName("net.minecraft.world.entity.Display$GenericInterpolator");
        var progressField = display.getDeclaredField("lastProgress"); progressField.setAccessible(true);
        StringBuilder rows = new StringBuilder();
        long ms = System.currentTimeMillis() % 100000;
        Object pose = entity.getMethod("getPose").invoke(player);
        double playerY = (double) entity.getMethod("getY").invoke(player);
        int index = 0;
        for (Object rider : (java.util.List<?>) entity.getMethod("getPassengers").invoke(player)) {
            if (!display.isInstance(rider)) continue;
            Object state = display.getMethod("renderState").invoke(rider); if (state == null) continue;
            Object curve = state.getClass().getMethod("transformation").invoke(state);
            Object transform = interpolator.getMethod("get", float.class).invoke(curve, progressField.getFloat(rider));
            Object translation = transform.getClass().getMethod("translation").invoke(transform);
            float transY = (float) Class.forName("org.joml.Vector3fc").getMethod("y").invoke(translation);
            double y = (double) entity.getMethod("getY").invoke(rider), yo = entity.getField("yo").getDouble(rider);
            double riderY = yo + (y - yo) * partial;
            rows.append(ms).append('\t').append(pose).append('\t').append(cameraY).append('\t').append(playerY).append('\t').append(index++)
                    .append('\t').append(riderY).append('\t').append(transY).append('\t').append(riderY + transY - cameraY).append('\n');
        }
        return rows.toString();
    }

    private static void failure(Path directory, Exception error) {
        try { Files.writeString(directory.resolve("error.txt"), error.toString()+"\n"+java.util.Arrays.toString(error.getStackTrace())); } catch (Exception ignored) { }
        error.printStackTrace();
    }
}
