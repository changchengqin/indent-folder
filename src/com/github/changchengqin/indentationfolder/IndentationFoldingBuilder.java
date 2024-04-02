package com.github.changchengqin.indentationfolder;

import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilder;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * 1. 首先判断类注释中有没有#use indentation-based folding strategy#,如果没有，就不对当前文件进行处理
 * 3. 找到每个方法中htmlflow start 和 htmlflow end单行注释所在行号，如果找不到这两个单行注释，就计算每个方法内容的首尾行号
 * 4. 根据首尾行号进行IndentationFold处理
 * #use indentation-based folding strategy#
 */
public class IndentationFoldingBuilder implements FoldingBuilder {
    int tabSize = 4;
    @Override
    public FoldingDescriptor[] buildFoldRegions(@NotNull ASTNode node, @NotNull Document document) {

        PsiFile psiFile = node.getPsi().getContainingFile();
        List<FoldingDescriptor> descriptors = new ArrayList<>();

        if (psiFile == null) {
            return FoldingDescriptor.EMPTY_ARRAY;
        }

        PsiDocComment psiDocComment = PsiTreeUtil.findChildOfType(node.getPsi(), PsiDocComment.class);

        if(psiDocComment == null) {
            return FoldingDescriptor.EMPTY_ARRAY;
        }

        if(!psiDocComment.getText().contains("use indentation-based folding strategy")){
            return FoldingDescriptor.EMPTY_ARRAY;
        }

        Collection<PsiMethod> methods = PsiTreeUtil.findChildrenOfType(node.getPsi(), PsiMethod.class);

        for (PsiMethod method : methods) {
            processMethod(method, descriptors, document);
        }
        return descriptors.toArray(new FoldingDescriptor[0]);
    }

    private void processMethod(@NotNull PsiMethod psiMethod, List<FoldingDescriptor> descriptors, Document document) {
        PsiCodeBlock body = psiMethod.getBody();
        Collection<PsiComment> psiComments = PsiTreeUtil.findChildrenOfType(body, PsiComment.class);

        int indentationStartLineNumber = -1;
        int indentationEndLineNumber = -1;

        for (PsiComment psiComment : psiComments) {
            if (indentationStartLineNumber > -1 && indentationEndLineNumber > -1) {
                return;
            }
            if (psiComment.getTokenType() != JavaTokenType.END_OF_LINE_COMMENT) {
                continue;
            }
            if (indentationStartLineNumber == -1 && psiComment.getText().contains("indentation-based folding start")) {
                indentationStartLineNumber = document.getLineNumber(psiComment.getTextOffset()) + 1;
            } else if (indentationEndLineNumber == -1 && psiComment.getText().contains("indentation-based folding end")) {
                indentationEndLineNumber = document.getLineNumber(psiComment.getTextOffset());
            }
        }

        if (indentationStartLineNumber == -1) {
            TextRange bodyTextRange = body.getTextRange();
            indentationStartLineNumber = document.getLineNumber(bodyTextRange.getStartOffset()) + 1;
            indentationEndLineNumber = document.getLineNumber(bodyTextRange.getEndOffset()) ;
        }

        if(indentationStartLineNumber == indentationEndLineNumber -1) {
            return;
        }

        List<LineIndent> indentLevels = new ArrayList<>();
        List<Integer> emptyLines = new ArrayList<>();

        // Step 1: Calculate indent levels for each line
        for (int i = indentationStartLineNumber; i <= indentationEndLineNumber; i++) {
            String lineText = document.getText(new TextRange(document.getLineStartOffset(i), document.getLineEndOffset(i)));
            int indentLevel = getIndentLevel(lineText);
            indentLevels.add(new LineIndent(i,indentLevel));
            if(lineText.trim().isEmpty()) {
                emptyLines.add(i);
            }
        }

        // Step 2: Traverse the array to find fold regions
        for (int i = 0; i < indentLevels.size() - 1; i++) {
            LineIndent currentIndent = indentLevels.get(i);
            LineIndent nextIndent = indentLevels.get(i + 1);
            int startOffset = document.getLineStartOffset(currentIndent.lineNumber);

            if(isExcludeLine(currentIndent.lineNumber, startOffset,body,emptyLines)){
                continue;
            }

            if (nextIndent.indentLevel > currentIndent.indentLevel) {
                int endLineNumber = findEndLineNumber(i+2, indentLevels, currentIndent,document,body,emptyLines);

                if (endLineNumber > -1) {
                    FoldingDescriptor namedFoldingDescriptor = new FoldingDescriptor(
                            psiMethod.getNode(),
                            new TextRange(startOffset + currentIndent.indentLevel * 4, document.getLineEndOffset(endLineNumber)),
                            null,
                            getPlaceholderText(document, currentIndent.lineNumber)
                    );
                    descriptors.add(namedFoldingDescriptor);
                }
            }
        }
    }

    private  int findEndLineNumber(int startIndex, List<LineIndent> indentLevels, LineIndent currentIndent,Document document,PsiCodeBlock body,List<Integer> emptyLines) {
        for (int j = startIndex; j < indentLevels.size() - 1; j++) {
            LineIndent endlineIndent= indentLevels.get(j);
            if (endlineIndent.indentLevel.equals(currentIndent.indentLevel)) {
                if(isExcludeLine(endlineIndent.lineNumber,document.getLineStartOffset(endlineIndent.lineNumber),body,emptyLines)){
                    continue;
                }
                return endlineIndent.lineNumber;
            }
        }
        return -1;
    }

    private boolean isExcludeLine(int lineNumber,int startOffset,PsiCodeBlock body,List<Integer> emptyLines) {
        PsiElement psiElementInLine = body.findElementAt(startOffset);
        // exclude comment line
        if (psiElementInLine instanceof PsiComment) {
            return true;
        }

        // exclude blank line
        return emptyLines.contains(lineNumber);
    }

    public static String getLeadingSpaces(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }

        int start = 0;
        while (start < input.length() && input.charAt(start) == ' ') {
            start++;
        }

        return input.substring(0, start);
    }

    private int getIndentLevel(String text) {
        text = getLeadingSpaces(text);
        int count = 0;

        for (char c : text.toCharArray()) {
            if (c == ' ') {
                count++;
            } else {
                break;
            }
        }
        return count / this.tabSize; // Assuming each indentation level equals two spaces
    }

    public String getPlaceholderText(Document document, int lineNumber) {
        String lineText = document.getText(new TextRange(document.getLineStartOffset(lineNumber), document.getLineEndOffset(lineNumber)));
            String[] lineTextSplitByDot = lineText.stripLeading().split("\\.");
        System.out.println("hello");

        if (lineTextSplitByDot.length > 1) {
            return "." + lineTextSplitByDot[1] + "...";
        }
        if (lineText.stripLeading().length() > 5) {
            return lineText.stripLeading().substring(0, 5) + "...";
        } else {
            return lineText.stripLeading() + "...";
        }

    }

    @Nullable
    @Override
    public String getPlaceholderText(@NotNull ASTNode node) {
        return null;
    }

    @Override
    public boolean isCollapsedByDefault(@NotNull ASTNode node) {
        return false; // Default state of the folded region (collapsed or expanded)
    }

}
