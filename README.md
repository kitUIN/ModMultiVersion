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


目前支持:
- 双向同步
- 单向同步
- 白名单
- 黑名单
- json5单向为json
## 规范
### 项目结构规范
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

> 加载器origin 优先级高于 全局级origin

示例:

全局origin文件夹下有文件`src/main/java/io/github/kituin/chatimage/client/ChatImageClient.java`,他将会被复制到`{loader}/{loader-version}/src/main/java/io/github/kituin/chatimage/client/ChatImageClient.java`

比如:复制到`fabric/fabric-1.20/...../ChatImageClient.java`, `forge/forge-1.18/...../ChatImageClient.java`

如果origin是在加载器目录下,则只会复制到当前加载器内的版本文件夹下

### 语法规范
语法解析依赖于[ModMultiVersionInterpreter](https://github.com/kitUIN/ModMultiVersionInterpreter)

### 语法
`IF-ELSE`模式
- 必须以`END IF`结尾
- 注释支持`//`与`#`
- 关键字全大写
- 允许使用`IF`,`ELSE`,`ELSE IF`
- 允许使用`或`,`非`,`与`运算
- 允许使用`>`,`<`,`<=`,`>=`,`==`,`!=`, 不填默认为`==`

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

## 手动同步
`Ctrl+S`手动保存即可触发同步

右键同步到的文件点击`从磁盘重新加载`

有时idea的文件系统来不及检测变更,请善用`从磁盘重新加载`

## 双向同步
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
右键origin文件夹中的文件,选择`设置多版本单向同步`

单向情况下,将会删除多版本代码的注释

注意: 如果启用单向之后又改回双向,立刻在源文件中进行手动同步,请注意不要触发反向修改,不然会损坏原文件

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

右键origin文件夹中的文件,选择`设置多版本同步黑名单`

在黑名单的文件夹将不被同步

添加黑名单文件夹:`{loader}/{loader-version}`

示例:`fabric/fabric-1.20.1` ... 请根据自己实际项目填写

## 白名单
与黑名单相似

##json5单向为json
如果对`.json5`文件启用`单向同步`,则被同步的文件将被改为`.json`
