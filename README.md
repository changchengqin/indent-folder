# IndentFolder
Plugin for IntelliJ platform IDEs.

Enable folding by identation,fix <a href="https://youtrack.jetbrains.com/issue/IDEA-320234">IDEA-320234</a>


## Usage
1. Download the latest version of the plugin JAR file from [<a href="https://youtrack.jetbrains.com/issue/IDEA-320234">Releases</a>](https://github.com/changchengqin/indent-folder/releases) to your local machine.
2. Open your IntelliJ Platform IDE, navigate to `Settings -> Plugins`, and install the plugin using `Install Plugin From Disk...`
3. In class-level comments, mark the current class for enabling this plugin with the following keywords:
    ```java
    package com.github.changchengqin.indentationfolder;
    
    import java.util.*;
    
    /**
     * use indentation-based folding strategy
     */
    public class IndentationFoldingBuilder implements FoldingBuilder {
    
    }
    ```
4. Additionally, within methods, you can specify the scope of code to be affected by this plugin using two single-line comments.
    ```java
    private void methodName() {
        
        // indentation-based folding start
          code...
        // indentation-based folding end
    
    }
    ```
5. example
   
   ![image](https://github.com/changchengqin/indent-folder/assets/3336443/2b24330e-1407-4a44-a3ab-ed990eb7a6ce)
