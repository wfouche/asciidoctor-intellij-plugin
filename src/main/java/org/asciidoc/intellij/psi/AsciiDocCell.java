package org.asciidoc.intellij.psi;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import icons.AsciiDocIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Alexander Schwartz
 */
public class AsciiDocCell extends ASTWrapperPsiElement {
  public AsciiDocCell(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public Icon getIcon(int flags) {
    return AsciiDocIcons.Structure.BLOCK;
  }

}
