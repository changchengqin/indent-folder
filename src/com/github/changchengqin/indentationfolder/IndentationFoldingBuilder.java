package com.github.changchengqin.indentationfolder;

import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilder;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * use indentation-based folding strategy
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
            if(method.getBody() == null) {
                continue;
            }
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
                            new TextRange(startOffset, document.getLineEndOffset(endLineNumber)),
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
        return lineText + "...";
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
