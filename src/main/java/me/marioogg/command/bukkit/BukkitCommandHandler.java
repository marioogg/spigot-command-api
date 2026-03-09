package me.marioogg.command.bukkit;

import com.google.common.reflect.ClassPath;
import lombok.Getter;
import lombok.SneakyThrows;
import me.marioogg.command.Command;
import me.marioogg.command.bukkit.node.CommandNode;
import me.marioogg.command.bukkit.parameter.ParamProcessor;
import me.marioogg.command.bukkit.parameter.Processor;
import me.marioogg.command.common.help.Help;
import me.marioogg.command.common.help.HelpNode;
import org.bukkit.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

public class BukkitCommandHandler {

    @Getter
    private static Plugin plugin;
    @Getter
    private static Logger logger;

    public static void setPlugin(Plugin plugin) {
        BukkitCommandHandler.plugin = plugin;
        logger = LoggerFactory.getLogger(plugin.getName());
    }

    @SneakyThrows
    public static void registerCommands(String path, Plugin plugin) {
        ClassPath.from(plugin.getClass().getClassLoader()).getAllClasses().stream()
                .filter(info -> info.getPackageName().startsWith(path))
                .filter(info -> isInstantiable(info.load()))
                .forEach(info -> registerCommands(info.load(), plugin));
    }

    @SneakyThrows
    public static void registerCommands(Class<?> commandClass, Plugin plugin) {
        BukkitCommandHandler.setPlugin(plugin);
        if (!isInstantiable(commandClass)) return;
        registerCommands(commandClass.getDeclaredConstructor().newInstance());
    }

    @SneakyThrows
    public static void registerCommands(Plugin plugin, Class<?>... commandClasses) {
        BukkitCommandHandler.setPlugin(plugin);
        for (Class<?> commandClass : commandClasses) {
            if (!isInstantiable(commandClass)) continue;
            registerCommands(commandClass.getDeclaredConstructor().newInstance());
        }
    }

    public static void registerCommands(Object commandClass) {
        Arrays.stream(commandClass.getClass().getDeclaredMethods()).forEach(method -> {
            Command command = method.getAnnotation(Command.class);
            if (command == null) return;
            new CommandNode(commandClass, method, command);
        });

        Arrays.stream(commandClass.getClass().getDeclaredMethods()).forEach(method -> {
            Help help = method.getAnnotation(Help.class);
            if (help == null) return;
            HelpNode helpNode = new HelpNode(commandClass, help.names(), help.permission(), method);
            CommandNode.getNodes().forEach(node -> node.getNames().forEach(name -> Arrays.stream(help.names())
                    .map(String::toLowerCase)
                    .filter(helpName -> name.toLowerCase().startsWith(helpName))
                    .forEach(helpName -> node.getHelpNodes().add(helpNode))));
        });
    }

    @SneakyThrows
    public static void registerProcessors(String path, Plugin plugin) {
        ClassPath.from(plugin.getClass().getClassLoader()).getAllClasses().stream()
                .filter(info -> info.getPackageName().startsWith(path))
                .filter(info -> isInstantiable(info.load()))
                .filter(info -> info.load().getSuperclass().equals(Processor.class))
                .forEach(info -> {
                    try {
                        ParamProcessor.createProcessor((Processor<?>) info.load().getDeclaredConstructor().newInstance());
                    } catch (Exception e) {
                        logger.error("Error registering command processors: ", e);
                    }
                });
    }

    public static void registerProcessor(Processor<?> processor) {
        ParamProcessor.createProcessor(processor);
    }

    public static void registerProcessors(Processor<?>... processors) {
        Arrays.stream(processors).forEach(BukkitCommandHandler::registerProcessor);
    }

    private static boolean isInstantiable(Class<?> clazz) {
        return !clazz.isAnonymousClass()
                && !clazz.isLocalClass()
                && !clazz.isInterface()
                && !clazz.isEnum()
                && !java.lang.reflect.Modifier.isAbstract(clazz.getModifiers())
                && (!clazz.isMemberClass() || java.lang.reflect.Modifier.isStatic(clazz.getModifiers()))
                && Arrays.stream(clazz.getDeclaredConstructors())
                .anyMatch(c -> c.getParameterCount() == 0);
    }
}