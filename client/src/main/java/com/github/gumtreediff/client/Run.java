/*
 * This file is part of GumTree.
 *
 * GumTree is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * GumTree is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with GumTree.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Copyright 2011-2015 Jean-Rémy Falleri <jr.falleri@gmail.com>
 * Copyright 2011-2015 Floréal Morandat <florealm@gmail.com>
 */

package com.github.gumtreediff.client;
import com.github.gumtreediff.actions.EditScript;
import com.github.gumtreediff.actions.EditScriptGenerator;
import com.github.gumtreediff.actions.SimplifiedChawatheScriptGenerator;
import com.github.gumtreediff.actions.model.Action;
import com.github.gumtreediff.gen.TreeGenerators;
import com.github.gumtreediff.gen.c.CTreeGenerator;
import com.github.gumtreediff.io.ActionsIoUtils;
import com.github.gumtreediff.io.TreeIoUtils;
import com.github.gumtreediff.matchers.MappingStore;
import com.github.gumtreediff.tree.Tree;
import com.github.gumtreediff.tree.TreeContext;
import com.github.gumtreediff.utils.Registry;
import com.github.gumtreediff.gen.TreeGenerator;
import com.github.gumtreediff.matchers.Matcher;
import com.github.gumtreediff.matchers.Matchers;
import com.sun.tools.javac.Main;
import org.atteo.classindex.ClassIndex;

import java.io.*;


import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.*;

public class Run {

    public static class Options implements Option.Context {
        @Override
        public Option[] values() {
            return new Option[]{
                    new Option("-C", "Set system property (-c property value). ",
                            2) {

                        @Override
                        protected void process(String name, String[] args) {
                            System.setProperty(args[0], args[1]);
                        }
                    },
                    new Option.Verbose(),
                    new Help(this)
            };
        }
    }

    public static void initGenerators() {
        ClassIndex.getSubclasses(TreeGenerator.class).forEach(
                gen -> {
                    com.github.gumtreediff.gen.Register a =
                            gen.getAnnotation(com.github.gumtreediff.gen.Register.class);
                    if (a != null)
                        TreeGenerators.getInstance().install(gen, a);
                });
    }

    public static void initMatchers() {
        ClassIndex.getSubclasses(Matcher.class).forEach(
                gen -> {
                    com.github.gumtreediff.matchers.Register a =
                            gen.getAnnotation(com.github.gumtreediff.matchers.Register.class);
                    if (a != null)
                        Matchers.getInstance().install(gen, a);
                });
    }

    public static void initClients() {
        ClassIndex.getSubclasses(Client.class).forEach(
                cli -> {
                    com.github.gumtreediff.client.Register a =
                            cli.getAnnotation(com.github.gumtreediff.client.Register.class);
                    if (a != null)
                        Clients.getInstance().install(cli, a);
                });
    }

    static {
        initGenerators();
        initMatchers();
    }

    public static void startClient(String name, Registry.Factory<? extends Client> client, String[] args) {
        try {
            Client inst = client.newInstance(new Object[]{ args });
            try {
                inst.run();
            } catch (Exception e) {
                System.err.printf("Error while running client '%s'.\n", name);
                e.printStackTrace();
            }
        } catch (InvocationTargetException e) {
            System.err.printf("Error while parsing arguments of client '%s'.\n", name);
            e.printStackTrace();
        } catch (InstantiationException | IllegalAccessException e) {
            System.err.printf("Can't instantiate client '%s'.", name);
            e.printStackTrace();
        }
    }

    //new function --- find leaf nodes whose parent-node is Ident
    public static void findNodes(Tree node, List<String> Nodes){
        if (node == null)return;

        if (node.getType().toString().equals("GenericString")) {
            // 查找父节点的父节点
            Tree Parent = node.getParent();
            if (Parent != null && Parent.getType().toString().equals("Ident")) {
                Nodes.add(node.getLabel());

            }
        }
        if (!node.isLeaf()){// 需要判断这个节点是否是叶子节点
            for (int i = 0; i < node.getChildren().size(); i++) {
                findNodes(node.getChild(i), Nodes);
            }
        }else {
            return;
        }

    }

    public static void findFunCall(Tree node, List<Tree>FunCallNode){
        if(node == null)return;
        if(node.getType().toString().equals("FunCall")){
            FunCallNode.add(node);
            return;
        }
        if (!node.isLeaf()){// 需要判断这个节点是否是叶子节点
            for (int i = 0; i < node.getChildren().size(); i++) {
                findFunCall(node.getChild(i), FunCallNode);
            }
        }else {
            return;
        }

    }


    //new function --- print diff information
    public static void printEditScript(EditScript editScript) {

        System.out.println("Edit Script Information:");
        //System.out.println("Number of actions: " + editScript.size());

        int index = 1;
        for (Action action : editScript) {
            //System.out.println(action);
            if (action.getName().equals("insert-tree") || action.getName().equals("delete-tree") | action.getName().equals("insert-node")) {
                List<Tree> funCallNodes = new ArrayList<>();
                findFunCall(action.getNode(), funCallNodes);
                if (funCallNodes.size() > 0) {
                    //System.out.println("Action " + index + ": " + action);
                }
                for (Tree funcallnode : funCallNodes) {
                    System.out.println(funcallnode.toString());
                    List<String> nodes = new ArrayList<>();
                    findNodes(funcallnode, nodes);
                    int flag = 0;
                    for (String node : nodes) {
                        if (flag == 0) {
                            System.out.println("函数名: " + node);
                            flag = 1;
                        } else {
                            System.out.println("参数: " + node);
                        }
                    }
                }
            }else if(action.getName().equals("update-node")){
                Tree node1 = action.getNode().getParent();
                if((node1) != null){
                    if(node1.getParent().getType().toString().equals("FunCall")){
                        System.out.println(action);
                        System.out.println("函数名: " + action.getNode().getLabel());
                    }
                }
            }
            index++;
        }

    }

    public static void printEditScriptInfoToFile(EditScript editScript) {

        logger.info("Edit Script Information:");
        logger.info("Number of actions: " + editScript.size());
        int index = 1;
        for (Action action : editScript) {

            if (action.getName().equals("insert-tree") || action.getName().equals("delete-tree") ||action.getName().equals("insert-node")) {
                List<Tree> funCallNodes = new ArrayList<>();
                findFunCall(action.getNode(), funCallNodes);
                if (funCallNodes.size() > 0) {
                    logger.info("Action " + index + ": " + action);
                }
                for (Tree funcallnode : funCallNodes) {
                    logger.info(funcallnode.toString());
                    List<String> nodes = new ArrayList<>();
                    findNodes(funcallnode, nodes);
                    int flag = 0;
                    for (String node : nodes) {
                        if (flag == 0) {
                            logger.info("函数名: " + node);
                            flag = 1;
                        } else {
                            logger.info("参数: " + node);
                        }
                    }
                }
            }else if(action.getName().equals("update-node")){
                Tree node1 = action.getNode().getParent();
                if((node1) != null){
                    if(node1.getParent().getType().toString().equals("FunCall")){
                        logger.info(action.toString());
                        logger.info("函数名: " + action.getNode().getLabel());
                    }
                }
            }
            index++;
        }

    }

    //new function --- read information form file
    public static String readFile(String filePath){
        StringBuilder fileContent = new StringBuilder();

        try {
            // 打开文件
            BufferedReader reader = new BufferedReader(new FileReader(filePath));
            String line;

            // 逐行读取文件内容并添加到字符串构建器中
            while ((line = reader.readLine()) != null) {
                fileContent.append(line).append("\n"); // 添加换行符以保持文件原有的行分隔
            }

            // 关闭文件
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return fileContent.toString();
    }
    /*
     * update node -- before3.c 还没解决
     * TRUE 与 true 还没解决
     * */
    static class MyFormatter extends Formatter {
        @Override
        public String format(LogRecord record) {
            // 仅返回日志消息本身，结尾加上换行
            return record.getMessage() + "\n";
        }
    }
    private static final Logger logger = Logger.getLogger(Main.class.getName());

    static {
        try {
            String userHome = System.getProperty("user.home");
            String fileName = userHome + "/Desktop/PPatHF_share/output.log";

            // 创建文件处理器，并设置日志文件滚动
            FileHandler fileHandler = new FileHandler(fileName, true);
            // 设置不带时间戳的日志格式化器
            fileHandler.setFormatter(new MyFormatter());

            logger.addHandler(fileHandler);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] origArgs) throws IOException {
        Run.initGenerators(); // registers the available parsers
        // 获取用户的主目录
        String userHome = System.getProperty("user.home");
        // 创建 Logger 对象
        // 构建文件路径
        //String path1 = userHome + "/Desktop/科研/PPatHF_share/examples/before4.c";
        //String path2 = userHome + "/Desktop/科研/PPatHF_share/examples/after4.c";
        //String path = userHome + "/Desktop/科研/PPatHF_share/out.json";
        // String fileName = userHome + "/Desktop/PPatHF_share/output.log";
        // PrintWriter writer = new PrintWriter(new FileWriter(fileName));
        // 得到input
        String input_src = "void msg_source(int attr)\n" +
                " {\n" +
                "     char_u\t*p;\n" +
                "\n" +
                "     ++no_wait_return;\n" +
                "     p = get_emsg_source();\n" +
                "\n" +
                "     // remember the last sourcing name printed, also when it's empty\n" +
                "     if (SOURCING_NAME == NULL || other_sourcing_name())\n" +
                "     {\n" +
                " \tvim_free(last_sourcing_name);\n" +
                " \tif (SOURCING_NAME == NULL)\n" +
                " \t    last_sourcing_name = NULL;\n" +
                " \telse\n" +
                " \t    last_sourcing_name = vim_strsave(SOURCING_NAME);\n" +
                "     }\n" +
                "     --no_wait_return;\n" +
                " }\n";
        String input_dst = "void msg_source(int attr)\n" +
                " {\n" +
                "     char_u\t*p;\n" +
                "     static int\trecursive = FALSE;\n" +
                "\n" +
                "     // Bail out if something called here causes an error.\n" +
                "     if (recursive)\n" +
                " \treturn;\n" +
                "     recursive = TRUE;\n" +
                "\n" +
                "     ++no_wait_return;\n" +
                "     p = get_emsg_source();\n" +
                "     // remember the last sourcing name printed, also when it's empty\n" +
                "     if (SOURCING_NAME == NULL || other_sourcing_name())\n" +
                "     {\n" +
                " \tVIM_CLEAR(last_sourcing_name);\n" +
                " \tif (SOURCING_NAME != NULL)\n" +
                " \t    last_sourcing_name = vim_strsave(SOURCING_NAME);\n" +
                "     }\n" +
                "     --no_wait_return;\n" +
                "\n" +
                "     recursive = FALSE;\n" +
                " }\n";
        Tree src = new CTreeGenerator().generateFrom().string(input_src).getRoot();
        Tree dst = new CTreeGenerator().generateFrom().string(input_dst).getRoot();
        //System.out.println(src.toTreeString());
        //System.out.println(dst.toTreeString());
        Matcher defaultMatcher = Matchers.getInstance().getMatcher(); // retrieves the default matcher
        MappingStore mappings = defaultMatcher.match(src, dst); // computes the mappings between the trees
        EditScriptGenerator editScriptGenerator = new SimplifiedChawatheScriptGenerator(); // instantiates the simplified Chawathe script generator
        EditScript actions = editScriptGenerator.computeActions(mappings); // computes the edit script
        printEditScript(actions);
/*
        String jsonString = readFile(path);
        // 移除掉 ConsoleHandler，这样日志就只会写入到文件中
        logger.setUseParentHandlers(false);
        JSONArray jsonArray = new JSONArray(jsonString); // 示例使用JSON.simple库
        for (Object obj : jsonArray) {
            JSONObject jsonObject = (JSONObject) obj;
            String input1 = jsonObject.getString("func_before_target");
            String input2 = jsonObject.getString("func_after_source");

            Tree src = new CTreeGenerator().generateFrom().string(input1).getRoot();
            Tree dst = new CTreeGenerator().generateFrom().string(input2).getRoot();
            //System.out.println(src.toTreeString());
            //System.out.println(dst.toTreeString());
            Matcher defaultMatcher = Matchers.getInstance().getMatcher(); // retrieves the default matcher
            MappingStore mappings = defaultMatcher.match(src, dst); // computes the mappings between the trees
            EditScriptGenerator editScriptGenerator = new SimplifiedChawatheScriptGenerator(); // instantiates the simplified Chawathe script generator
            EditScript actions = editScriptGenerator.computeActions(mappings); // computes the edit script
            // 记录日志信息
            logger.info("-------------- --------begin-------- ------------");
            logger.info("commit_id_source: " + jsonObject.getString("commit_id_source"));
            logger.info("diff_source: " + jsonObject.getString("diff_source"));
            printEditScriptInfoToFile(actions);
            logger.info("-------------- ---------end---------- ------------");
        }*/

    }

    public static void displayHelp(PrintStream out, Option.Context ctx) {
        out.println("Available Options:");
        Option.displayOptions(out, ctx);
        out.println();
        listCommand(out);
    }

    public static void listCommand(PrintStream out) {
        out.println("Available Commands:");
        for (Registry.Entry cmd: Clients.getInstance().getEntries())
            out.println("* " + cmd);
    }

    static class Help extends Option.Help {
        public Help(Context ctx) {
            super(ctx);
        }

        @Override
        public void process(String name, String[] args) {
            displayHelp(System.out, context);
        }
    }
}
