package info.kgeorgiy.ja.karpov.crawler;

import info.kgeorgiy.java.advanced.crawler.*;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.*;
import java.util.concurrent.*;

public class WebCrawler implements AdvancedCrawler {
    private static final int DEFAULT_ARGUMENT = 10;
    private final int perHost;
    private final Downloader downloader;
    private final ExecutorService downloadExecutor;
    private final ExecutorService extractExecutor;

    /**
     * Constructs an instance of the class.
     *
     * @param downloader  - the downloader to use in {@link #download} method.
     * @param downloaders - the maximum number of pages downloaded concurrently.
     * @param extractors  - the maximum number of pages from which links are downloaded concurrently.
     * @param perHost     - the maximum number of pages downloaded per one host concurrently.
     */
    public WebCrawler(Downloader downloader, int downloaders, int extractors, int perHost) {
        this.perHost = perHost;
        this.downloader = downloader;
        downloadExecutor = Executors.newFixedThreadPool(downloaders);
        extractExecutor = Executors.newFixedThreadPool(extractors);
    }

    private void addUrl(Set<String> visitedSet, Set<String> hostSet,
                        Set<String> urlSet, Map<String, Semaphore> map, String s, boolean ignoreHosts) {
        try {
            String host = URLUtils.getHost(s);
            if ((ignoreHosts || hostSet.contains(host)) && visitedSet.add(s)) {
                urlSet.add(s);
                map.computeIfAbsent(host, (x) -> new Semaphore(perHost));
            }
        } catch (MalformedURLException ignored) {
        }
    }

    private void extractSubmit(Phaser ph, Document document, Map<String, Semaphore> hostSemaphores, boolean ignoreHosts,
                               Set<String> nextUrlsToAdd, Set<String> hosts, Set<String> usedUrls) {
        ph.register();
        extractExecutor.submit(() -> {
            try {
                document.extractLinks().forEach(urlToAdd ->
                        addUrl(usedUrls, hosts, nextUrlsToAdd, hostSemaphores, urlToAdd, ignoreHosts));
            } catch (IOException ignored) {
            } finally {
                ph.arriveAndDeregister();
            }
        });
    }

    private void downloadSubmit(Phaser ph, Map<String, Semaphore> hostSemaphores, String urlToDownload,
                                Map<String, IOException> errors, List<String> visitedSites,
                                int iteration, int depth, boolean ignoreHosts, Set<String> nextUrlsToAdd,
                                Set<String> hosts, Set<String> usedUrls) {
        ph.register();
        downloadExecutor.submit(() -> {
            try {
                Semaphore hostOfUrlToDownload = hostSemaphores.get(URLUtils.getHost(urlToDownload));
                Document document;

                hostOfUrlToDownload.acquire();
                try {
                    document = downloader.download(urlToDownload);
                } catch (IOException e) {
                    errors.put(urlToDownload, e);
                    return;
                } finally {
                    hostOfUrlToDownload.release();
                }
                visitedSites.add(urlToDownload);

                if (iteration == depth - 1) {
                    return;
                }

                extractSubmit(ph, document, hostSemaphores, ignoreHosts, nextUrlsToAdd, hosts, usedUrls);
            } catch (InterruptedException | MalformedURLException ignored) {
            } finally {
                ph.arriveAndDeregister();
            }
        });
    }

    private Result download(String url, int depth, Set<String> hosts, boolean ignoreHosts) {
        Map<String, IOException> errors = new ConcurrentHashMap<>();
        Map<String, Semaphore> hostSemaphores = new ConcurrentHashMap<>();
        Set<String> usedUrls = Collections.synchronizedSet(new HashSet<>());
        List<String> visitedSites = Collections.synchronizedList(new ArrayList<>());
        Set<String> urlsToDownload = Collections.synchronizedSet(new HashSet<>());

        addUrl(usedUrls, hosts, urlsToDownload, hostSemaphores, url, ignoreHosts);

        Phaser ph = new Phaser(1);
        for (int i = 0; i < depth; i++) {
            Set<String> nextUrlsToAdd = Collections.synchronizedSet(new HashSet<>());

            for (String urlToDownload : urlsToDownload) {
                downloadSubmit(ph, hostSemaphores, urlToDownload, errors, visitedSites, i, depth,
                        ignoreHosts, nextUrlsToAdd, hosts, usedUrls);
            }
            urlsToDownload = nextUrlsToAdd;

            ph.arriveAndAwaitAdvance();
        }

        return new Result(visitedSites, errors);
    }

    /**
     * Downloads website up to specified depth.
     *
     * @param url   start <a href="http://tools.ietf.org/html/rfc3986">URL</a>.
     * @param depth download depth.
     * @param hosts domains to follow, pages on another domains should be ignored.
     * @return download result.
     */
    @Override
    public Result download(String url, int depth, List<String> hosts) {
        Set<String> acceptableHosts = Collections.synchronizedSet(new HashSet<>());
        acceptableHosts.addAll(hosts);
        return download(url, depth, acceptableHosts, false);
    }

    /**
     * Downloads website up to specified depth.
     *
     * @param url   start <a href="http://tools.ietf.org/html/rfc3986">URL</a>.
     * @param depth download depth.
     * @return download result.
     */
    @Override
    public Result download(String url, int depth) {
        return download(url, depth, null, true);
    }

    private void shutdownExecutor(ExecutorService executor) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                if (!executor.awaitTermination(60, TimeUnit.SECONDS))
                    System.err.println("Pool did not terminate");
            }
        } catch (InterruptedException ie) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Closes this web-crawler, relinquishing any allocated resources.
     */
    @Override
    public void close() {
        shutdownExecutor(downloadExecutor);
        shutdownExecutor(extractExecutor);
    }

    /**
     * Allows to call {@link #download} from the command line.
     * The arguments are: mandatory: url; optional: depth, maximum allowed number of downloader and extractor threads
     * and web-pages extracted simultaneously per host. Default value for optional arguments is available.
     *
     * @param args the arguments to pass to {@link #download} call
     */
    public static void main(String[] args) {
        if (args == null) {
            System.out.println("Null arguments provided");
            return;
        }
        if (args.length == 0 || args.length > 5) {
            System.out.println("Wrong number of arguments provided");
            return;
        }
        List<Integer> intArgs = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            try {
                if (args[i] == null) {
                    System.out.println("Null argument number " + (i + 1) + "provided.");
                    return;
                }
                intArgs.add(Integer.parseInt(args[i]));
            } catch (NumberFormatException e) {
                System.out.println("Non-numeric argument with number " + (i + 1) + " provided.");
                return;
            }
        }
        while (intArgs.size() < 4) {
            intArgs.add(DEFAULT_ARGUMENT);
        }

        Downloader localDownloader;
        try {
            localDownloader = new CachingDownloader(0);
        } catch (IOException e) {
            System.out.println("Could not create downloader " + e.getMessage());
            return;
        }
        try (Crawler crawler = new WebCrawler(localDownloader, intArgs.get(1), intArgs.get(2), intArgs.get(3))) {
            crawler.download(args[0], intArgs.get(0));
        }
    }
}