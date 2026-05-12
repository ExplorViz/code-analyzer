package net.explorviz.code.analysis.handler;

import net.explorviz.code.proto.FileData;

public abstract class AbstractFileDataHandler {

  protected final FileData.Builder builder;
  protected final String fileName;

  protected AbstractFileDataHandler(final String fileName) {
    this.fileName = fileName;
    this.builder = FileData.newBuilder().setFilePath(fileName);
  }

  public String getFileName() {
    return fileName;
  }

  public void setFileHash(final String fileHash) {
    builder.setFileHash(fileHash);
  }

  public void setRepositoryName(final String repositoryName) {
    builder.setRepositoryName(repositoryName);
  }

  public String getPackageName() {
    return builder.getPackageName();
  }

  public void setPackageName(final String packageName) {
    builder.setPackageName(packageName);
  }

  public void addImport(final String importName) {
    builder.addImportNames(importName);
  }

  public String addMetric(final String metricName, final String metricValue) {
    try {
      builder.putMetrics(metricName, Double.parseDouble(metricValue));
    } catch (NumberFormatException e) {
      builder.putMetrics(metricName, 0.0);
    }
    return metricValue;
  }

  public String getMetricValue(final String metricName) {
    return builder.getMetricsMap().containsKey(metricName)
        ? String.valueOf(builder.getMetricsMap().get(metricName))
        : null;
  }

  public void setModifications(final int modifiedLines, final int addedLines,
      final int deletedLines) {
    builder.setModifiedLines(modifiedLines);
    builder.setAddedLines(addedLines);
    builder.setDeletedLines(deletedLines);
  }

  public void setAuthor(final String author) {
    builder.setLastEditor(author);
  }

  public void setLandscapeToken(final String landscapeToken) {
    builder.setLandscapeToken(landscapeToken);
  }

  public abstract FileData getProtoBufObject();

  @Override
  public String toString() {
    return getProtoBufObject().toString();
  }
}
