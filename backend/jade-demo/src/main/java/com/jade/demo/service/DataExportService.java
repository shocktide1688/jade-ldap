package com.jade.demo.service;

import com.jade.common.api.R;
import com.jade.redis.lock.RedisLock;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 数据导出服务（演示看门狗自动续期）
 *
 * 业务场景：导出可能跑 1~10 分钟（数据量不定）
 *           如果用固定 TTL 锁，业务超过 TTL 锁过期 → 多节点重复执行
 *           用看门狗：锁永远不释放，业务结束才释放
 */
@ApplicationScoped
public class DataExportService {

    private static final Logger LOG = Logger.getLogger(DataExportService.class);

    @Inject
    RedisLock redisLock;

    /** 当前正在运行的导出任务数 */
    private final AtomicInteger runningCount = new AtomicInteger(0);

    /**
     * 模拟长时间导出
     *
     * @param taskName  任务名
     * @param totalSec  模拟总耗时（秒）
     */
    public R<String> export(String taskName, int totalSec) {
        String key = "lock:export:" + taskName;

        // 看门狗默认 30s lease，但业务跑 90s 也不会过期
        return redisLock.executeWithWatchdog(key, () -> {
            int n = runningCount.incrementAndGet();
            LOG.infof("[Export] START task=%s duration=%ds (active=%d)", taskName, totalSec, n);
            try {
                // 模拟耗时（不被打断、不超时）
                for (int i = 1; i <= totalSec; i++) {
                    Thread.sleep(1000);
                    LOG.infof("[Export] %s progress=%d/%ds activeWatchdogs=%d",
                            taskName, i, totalSec, redisLock.activeWatchdogs());
                }
                return R.ok("导出完成: " + taskName);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return R.fail(500, "导出被中断");
            } finally {
                runningCount.decrementAndGet();
                LOG.infof("[Export] END task=%s activeWatchdogs=%d",
                        taskName, redisLock.activeWatchdogs());
            }
        });
    }

    /**
     * 快速任务（无看门狗，用于对比）
     */
    public R<String> quickExport(String taskName) {
        String key = "lock:export:" + taskName;
        return redisLock.execute(key, Duration.ofSeconds(10), () -> {
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            return R.ok("快速导出完成: " + taskName);
        });
    }
}
