import java.time.Duration;
import java.time.LocalDateTime;

public class TmpId {
    private static final LocalDateTime EPOCH_TIME = LocalDateTime.of(2024, 1, 1, 0, 0, 0);

    static long gen(LocalDateTime now, long count) {
        long seconds = Duration.between(EPOCH_TIME, now).getSeconds();
        return (seconds << 32) | count;
    }

    public static void main(String[] args) {
        LocalDateTime now = LocalDateTime.now();
        long id1 = gen(now, 1);
        long id2 = gen(now.plusSeconds(1), 4294967295L);
        System.out.println("id1 = " + id1 + " , 有效bit数=" + Long.toBinaryString(id1).length());
        System.out.println("id2 = " + id2);
        System.out.println("均为正数(符号位0) = " + (id1 > 0 && id2 > 0));
        System.out.println("id1 时间戳部分 = " + (id1 >> 32) + " 秒 ≈ " + ((id1 >> 32) / 86400.0) + " 天");
        System.out.println("id2 序列号部分 = " + (id2 & 0xFFFFFFFFL));
        System.out.println("时间递增则 id 递增 = " + (id2 > id1));
        System.out.println("32bit 序列号可用年数 = " + (4294967295L / (365.0 * 86400)));
    }
}
