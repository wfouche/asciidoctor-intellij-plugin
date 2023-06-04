package org.asciidoc.intellij.psi;

import com.intellij.ide.actions.OpenFileAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.PsiNavigateUtil;
import org.asciidoc.intellij.file.AsciiDocFileType;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class AsciiDocFileUtil {

  private static final Logger LOG = Logger.getInstance(AsciiDocFileUtil.class);

  public static final Key<CachedValue<ProjectSectionCache>> KEY_ASCIIDOC_SECTIONS_IN_PROJECT = new Key<>("asciidoc-sections-in-project");

  public static ProjectSectionCache getProjectSectionCache(Project project) {
    CachedValue<ProjectSectionCache> cache = CachedValuesManager.getManager(project).createCachedValue(
      () -> CachedValueProvider.Result.create(new ProjectSectionCache(), PsiModificationTracker.MODIFICATION_COUNT));
    return ((UserDataHolderEx) project).putUserDataIfAbsent(KEY_ASCIIDOC_SECTIONS_IN_PROJECT, cache).getValue();
  }

  public static List<AsciiDocSection> findSections(Project project, String key) {
    if (key.length() == 0) {
      return Collections.emptyList();
    }
    ProjectSectionCache cache = getProjectSectionCache(project);
    List<AsciiDocSection> result = cache.get(key);
    if (result != null) {
      return result;
    }
    final GlobalSearchScope scope = new AsciiDocSearchScope(project).restrictedByAsciiDocFileType();
    Collection<AsciiDocSection> asciiDocSections = new ArrayList<>();
    asciiDocSections.addAll(AsciiDocSectionKeyIndex.getInstance().get(key, project, scope));
    asciiDocSections.addAll(AsciiDocSectionKeyIndex.getInstance().get(AsciiDocSectionStubElementType.SECTION_WITH_VAR, project, scope));
    for (AsciiDocSection asciiDocSection : asciiDocSections) {
      if (asciiDocSection.matchesTitle(key) || (asciiDocSection.matchesAutogeneratedId(key) && asciiDocSection.getBlockId() == null)) {
        if (result == null) {
          result = new ArrayList<>();
        }
        result.add(asciiDocSection);
      }
    }
    if (result == null) {
      result = Collections.emptyList();
    }
    result = Collections.unmodifiableList(result);
    cache.put(key, result);
    return result;
  }

  public static List<AsciiDocSection> findSections(Project project) {
    List<AsciiDocSection> result = new ArrayList<>();
    final GlobalSearchScope scope = new AsciiDocSearchScope(project).restrictedByAsciiDocFileType();
    AsciiDocSectionKeyIndex.getInstance().processAllElements(project,
      asciiDocSection -> {
        result.add(asciiDocSection);
        return true;
      }, scope);
    return result;
  }

  public static List<AsciiDocSection> findSections(PsiFile file) {
    List<AsciiDocSection> result = new ArrayList<>();
    GlobalSearchScope fileScope = GlobalSearchScope.FilesScope.fileScope(file);
    final GlobalSearchScope scope = new AsciiDocSearchScope(file.getProject()).restrictedByAsciiDocFileType().union(fileScope);
    AsciiDocSectionKeyIndex.getInstance().processAllElements(file.getProject(),
            asciiDocSection -> {
              result.add(asciiDocSection);
              return true;
            }, scope);
    return result;
  }

  public static void openInEditor(@NotNull URI uri, Editor parentEditor, VirtualFile parentDirectory) {
    ReadAction.run(() -> {
      if (parentEditor == null) {
        return;
      }
      String anchor = uri.getFragment();
      String path = uri.getPath();
      final VirtualFile targetFile;
      VirtualFile tmpTargetFile;
      String extension = AsciiDocFileType.getExtensionOrDefault(FileDocumentManager.getInstance().getFile(parentEditor.getDocument()));
      if (path.startsWith("/")) {
        if (SystemInfo.isWindows && path.matches("/[A-Z]:/.*")) {
          path = path.substring(1);
        }
        tmpTargetFile = LocalFileSystem.getInstance().findFileByPath(path);
        if (tmpTargetFile == null) {
          // extension might be skipped if it is an .adoc file
          tmpTargetFile = LocalFileSystem.getInstance().findFileByPath(path + "." + extension);
        }
        if (tmpTargetFile == null && path.endsWith(".html")) {
          // might link to a .html in the rendered output, but might actually be a .adoc file
          tmpTargetFile = LocalFileSystem.getInstance().findFileByPath(path.replaceAll("\\.html$", "." + extension));
        }
      } else {
        tmpTargetFile = parentDirectory.findFileByRelativePath(path);
        if (tmpTargetFile == null) {
          // extension might be skipped if it is an .adoc file
          tmpTargetFile = parentDirectory.findFileByRelativePath(path + "." + extension);
        }
        if (tmpTargetFile == null && path.endsWith(".html")) {
          // might link to a .html in the rendered output, but might actually be a .adoc file
          tmpTargetFile = parentDirectory.findFileByRelativePath(path.replaceAll("\\.html$", "." + extension));
        }
      }
      if (tmpTargetFile == null) {
        LOG.warn("unable to find file for " + uri);
        return;
      }
      targetFile = tmpTargetFile;

      Project project = ProjectUtil.guessProjectForContentFile(targetFile);
      if (project == null) {
        LOG.warn("unable to find project for " + uri);
        return;
      }

      if (targetFile.isDirectory()) {
        AsciiDocUtil.selectFileInProjectView(project, targetFile);
      } else {
        boolean anchorFound = false;
        if (anchor != null) {
          List<PsiElement> ids = AsciiDocUtil.findIds(project, targetFile, anchor);
          if (!ids.isEmpty()) {
            anchorFound = true;
            ApplicationManager.getApplication().invokeLater(() -> PsiNavigateUtil.navigate(ids.get(0)));
          }
        }

        if (!anchorFound) {
          ApplicationManager.getApplication().invokeLater(() -> OpenFileAction.openFile(targetFile, project));
        }
      }
    });
  }

}
