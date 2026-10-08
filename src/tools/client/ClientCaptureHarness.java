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
                            default -> throw new IllegalArgumentException("Unknown capture command " + text);
                        }
                        Files.writeString(directory.resolve("done.txt"), text);
                    } catch (Exception error) { failure(directory, error); }
                });
            }
        } catch (Exception error) { failure(directory, error); }
    }
    private static void failure(Path directory, Exception error) {
        try { Files.writeString(directory.resolve("error.txt"), error.toString()+"\n"+java.util.Arrays.toString(error.getStackTrace())); } catch (Exception ignored) { }
        error.printStackTrace();
    }
}
