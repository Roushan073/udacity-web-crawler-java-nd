package com.udacity.webcrawler;

import com.udacity.webcrawler.json.CrawlResult;
import com.udacity.webcrawler.parser.PageParser;
import com.udacity.webcrawler.parser.PageParserFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A concrete implementation of {@link WebCrawler} that runs multiple threads on a
 * {@link ForkJoinPool} to fetch and process multiple web pages in parallel.
 */
final class ParallelWebCrawler implements WebCrawler {
  private final Clock clock;
  private final PageParserFactory parserFactory;
  private final Duration timeout;
  private final int popularWordCount;
  private final int maxDepth;
  private final List<Pattern> ignoredUrls;
  private final ForkJoinPool pool;

  private Map<String, Integer> wordCountMap = Collections.synchronizedMap(new HashMap<>());
  private Set<String> visitedURLSet = Collections.synchronizedSet(new HashSet<>());
  private Instant crawlDeadline;

  @Inject
  ParallelWebCrawler(
      Clock clock,
      PageParserFactory parserFactory,
      @Timeout Duration timeout,
      @PopularWordCount int popularWordCount,
      @MaxDepth int maxDepth,
      @IgnoredUrls List<Pattern> ignoredUrls,
      @TargetParallelism int threadCount) {
    this.clock = clock;
    this.parserFactory = parserFactory;
    this.timeout = timeout;
    this.popularWordCount = popularWordCount;
    this.maxDepth = maxDepth;
    this.ignoredUrls = ignoredUrls;
    this.pool = new ForkJoinPool(Math.min(threadCount, getMaxParallelism()));
  }

  @Override
  public CrawlResult crawl(List<String> startingUrls) {
    crawlDeadline = clock.instant().plus(timeout);

    // Executing crawl in parallel using ForkJoinPool
    startingUrls.forEach(url -> pool.invoke(new WebCrawlTask(url, maxDepth)));

    if (wordCountMap.isEmpty()) {
      return new CrawlResult.Builder()
              .setWordCounts(wordCountMap)
              .setUrlsVisited(visitedURLSet.size())
              .build();
    }

    return new CrawlResult.Builder()
            .setWordCounts(WordCounts.sort(wordCountMap, popularWordCount))
            .setUrlsVisited(visitedURLSet.size())
            .build();
  }

  @Override
  public int getMaxParallelism() {
    return Runtime.getRuntime().availableProcessors();
  }

  /**
   * Class for crawling a URL and computing wordCount and recursively
   * execute crawl for child URLs
   */
  private class WebCrawlTask extends RecursiveTask<Void> {

    private String crawlURL;
    private int crawlURLDepth;

    WebCrawlTask(String crawlURL, int crawlURLDepth) {
      this.crawlURL = crawlURL;
      this.crawlURLDepth = crawlURLDepth;
    }

    @Override
    protected Void compute() {

      if (crawlURLDepth == 0 || clock.instant().isAfter(crawlDeadline)) {
        return null;
      }

      for (Pattern pattern : ignoredUrls) {
        if (pattern.matcher(crawlURL).matches()) {
          return null;
        }
      }

      if (visitedURLSet.contains(crawlURL)) {
        return null;
      }

      // Mark the URL as visited
      visitedURLSet.add(crawlURL);

      // Parse the webURL and get the wordCounts and hyperlinks (child URLs)
      PageParser.Result crawlResult = parserFactory.get(crawlURL).parse();

      // Update wordCount
      crawlResult.getWordCounts().forEach((word, count) ->
              wordCountMap.compute(word, (w, c) -> Objects.isNull(c) ? count : c + count));

      /*
       * Storing all the hyperlinks (child URLs) in a List as `WebCrawlTask` so that
       * they can be invoked at once using `invokeAll` method of `ForkJoinPool`
       */
      List<WebCrawlTask> childCrawlTasks = crawlResult.getLinks()
              .stream()
              .map(childURL -> new WebCrawlTask(childURL, crawlURLDepth - 1))
              .collect(Collectors.toList());

      invokeAll(childCrawlTasks);

      return null;
    }
  }
}


