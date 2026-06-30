package com.bugwiki.harness.cmd;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Bootstraps the first tutorial chapter and shows the target harness shape.
 *
 * @author zhaobinjie
 * @date 2026-06-24
 */
public class ClawApplication {
    public static void main(String[] args) {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.out.println("欢迎来到 tiny-claw-java 引擎启动序列");
        System.out.println("TODO 1. 初始化模型 Provider (大脑)");
        System.out.println("TODO 2. 初始化 Tool Registry (手脚)");
        System.out.println("TODO 3. 初始化上下文管理器 (内存管理器)");
        System.out.println("TODO 4. 组装并启动核心 Engine (操作系统心脏)");
        System.out.println("骨架搭建完毕，等待各模块注入！");
    }
}
