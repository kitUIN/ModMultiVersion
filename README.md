<p align="center">
  <img src="https://raw.githubusercontent.com/kitUIN/ModMultiVersion/master/src/main/resources/META-INF/pluginIcon.svg" width="350" height="220" alt="ModMultiVersion"></a>
</p>
<div align="center">

# ModMultiVersion

✨ 多版本代码同步-Idea插件 ✨

</div>
<p align="center">
  <a>
    <img src="https://img.shields.io/badge/license-MIT-green" alt="license">
  </a>
  <a >
    <img  src="https://img.shields.io/github/v/release/kitUIN/ModMultiVersion" alt="release">
  </a>
</p>


<!-- TOC -->
* [ModMultiVersion](#modmultiversion)
  * [项目结构规范](#项目结构规范)
  * [语法规范](#语法规范)
    * [关键字](#关键字)
    * [注释符号](#注释符号)
    * [布尔表达式](#布尔表达式)
    * [变量](#变量)
    * [IF-ELSE](#if-else)
    * [PRINT](#print)
  * [手动同步](#手动同步)
  * [双向同步](#双向同步)
  * [单向同步](#单向同步)
  * [黑名单](#黑名单)
  * [白名单](#白名单)
  * [重命名文件](#重命名文件)
<!-- TOC -->

## 项目结构规范

```
📦 ChatImage                # 项目名称
├── 📂 origin               # 全局级 用于同步
├── 📂 forge                # 加载器文件夹
│   ├── 📂 forge-1.20.1     # 1.20.1forge (加载器版本文件夹)
│   ├── 📂 ...              # 别的版本
│   └── 📂 origin           # 加载器级 用于同步
├── 📂 fabric               # 加载器文件夹
├── 📂 ...                  # 别的加载器
└── 📜 ...                  # 其他文件
```

用于同步的文件夹:

- origin (全局级)
- {loader}/origin(加载器级)

全局级origin文件夹下的所有文件将会以`相同相对路径`的方式,复制到`所有`的`加载器版本文件夹`下

加载器级origin文件夹同理,作用范围变为`当前加载器`下

> [!NOTE]  
> 加载器origin 优先级高于 全局级origin

示例:

全局origin文件夹下有文件`src/main/java/io/github/kituin/chatimage/client/ChatImageClient.java`
,他将会被复制到`{loader}/{loader-version}/src/main/java/io/github/kituin/chatimage/client/ChatImageClient.java`

比如:复制到`fabric/fabric-1.20/...../ChatImageClient.java`, `forge/forge-1.18/...../ChatImageClient.java`

如果origin是在加载器目录下,则只会复制到当前加载器内的版本文件夹下

## 语法规范

语法解析依赖于[ModMultiVersionInterpreter](https://github.com/kitUIN/ModMultiVersionInterpreter)

- `(` `)`
- `!` `&&` `||`
- `!=` `>` `>=` `<` `<=` `==`
- `&`识别为`&&`
- `|`识别为`||`
- `=`识别为`==`
- 支持变量自动替换
- 左部省略自动补充`$$ ==`

### 关键字

- 关键字必须全大写

| 关键字                            | 说明                     |
|--------------------------------|------------------------|
| `PRINT`                        | [调试输出](#print)         |
| `IF`/`END IF`/`ELSE`/`ELSE IF` | [IF-ELSE表达式](#if-else) |
| `EXCLUDE`                      | [黑名单](#黑名单)            |
| `ONLY`                         | [白名单](#白名单)            |
| `ONEWAY`                       | [单向同步](#单向同步)          |
| `RENAME`                       | [重命名文件](#重命名文件)        |

### 注释符号

- `//`
- `#`

### 布尔表达式

可以使用上述关键字进行组合

左部省略时自动补充`$$ ==`

最终计算时会替换掉变量

示例:

- `fabric-1.16.5`会自动识别为`$$ == fabric-1.16.5`
- `>=fabric-1.16.5`会自动识别为`$$ >= fabric-1.16.5`
- `fabric-1.16.5 || fabric-1.18.2`会自动识别为`$$ == fabric-1.16.5 || $$ == fabric-1.18.2`

### 变量

| 变量名                         | 类型     | 值          | 示例            |
|-----------------------------|--------|------------|---------------|
| `$$`                        | String | 加载器版本文件夹名称 | `1.20.1forge` |
| `$folder`                   | String | 文件所在文件夹名称  |               |
| `$loader`                   | String | 加载器名称(小写)  | `forge`       |
| `$fileName`                 | String | 文件名称(带后缀)  | `test.java`   |
| `$fileNameWithoutExtension` | String | 文件名称(无后缀)  | `test`        |

### IF-ELSE

- 注释符号开头
- 必须以`{注释符号} IF {布尔表达式}`开头
- 必须以`{注释符号} END IF`结尾
- 允许使用`{注释符号} ELSE`,`{注释符号} ELSE IF {布尔表达式}`

示例:

```java
    public static void setScreen(MinecraftClient client, Screen screen) {
// IF fabric-1.16.5
//        client.openScreen(screen);
// ELSE
//        client.setScreen(screen);
// END IF
}
```

在`fabric-1.16.5`文件夹中

```java
    public static void setScreen(MinecraftClient client, Screen screen) {
// IF fabric-1.16.5
    client.openScreen(screen);
// ELSE
//        client.setScreen(screen);
// END IF
}
```

其他文件夹中

```java
    public static void setScreen(MinecraftClient client, Screen screen) {
// IF fabric-1.16.5
//        client.openScreen(screen);
// ELSE
    client.setScreen(screen);
// END IF
}
```

### PRINT

输出调试,主要用于[变量](#变量)的调试

示例

```
// PRINT folder: $folder 
// PRINT loader: $loader
```

## 手动同步

`Ctrl+S`手动保存即可触发同步

右键同步到的文件点击`从磁盘重新加载`

有时idea的文件系统来不及检测变更,请善用`从磁盘重新加载`

## 双向同步

> [!NOTE]  
> 此为默认模式

顾名思义,你在origin文件夹内的修改会同步到版本文件夹内,你在版本文件夹内的修改也会同步到origin文件夹

需要注意的是:

```
📦 ChatImage                
├── 📂 origin               
│   └── 📜 A.java           
├── 📂 forge               
│   ├── 📂 forge-1.20.1
│   │   └── 📜 B.java     
│   ├── 📂 forge-1.16.5
│   │   └── 📜 C.java    
│   ├── 📂 forge-1.17.1
│   │   └── 📜 D.java           
│   └── 📂 origin           
├── 📂 fabric               
├── 📂 ...                  
└── 📜 ...                  
```

以上示例中,如果你修改了`B`,那么你需要打开一遍`A`才会将你在`B`中的修改同步到`C`和`D`中

## 单向同步

在文件的顶部使用关键字`ONEWAY`

用法:

```
{注释符号} ONEWAY
```

单向情况下,将会删除多版本代码的注释
> [!IMPORTANT]  
> 如果启用单向之后又改回双向,立刻在源文件中进行手动同步,请注意不要触发反向修改,不然会损坏原文件

示例:

```java
    public static void setScreen(MinecraftClient client, Screen screen) {
// IF fabric-1.16.5
//        client.openScreen(screen);
// ELSE
//        client.setScreen(screen);
// END IF
}
```

在`fabric-1.16.5`文件夹中

```java
    public static void setScreen(MinecraftClient client, Screen screen) {
    client.openScreen(screen);
}
```

其他文件夹中

```java
    public static void setScreen(MinecraftClient client, Screen screen) {
    client.setScreen(screen);
}
```

## 黑名单

在文件的顶部使用关键字`EXCLUDE`

用法:

```
{注释符号} EXCLUDE {布尔表达式}
```

## 白名单

在文件的顶部使用关键字`ONLY`

用法:

```
{注释符号} ONLY {布尔表达式}
```

## 重命名文件

在文件的顶部使用关键字`RENAME`

用法:

```
{注释符号} RENAME {带变量的字符串}
```

示例:

```
// RENAME $fileNameWithoutExtension.json
```
