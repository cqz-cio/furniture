package cn.iocoder.yudao.module.statistics.controller.admin.dashboard.vo;
import lombok.Data;import org.springframework.format.annotation.DateTimeFormat;import javax.validation.constraints.*;import java.time.LocalDate;
@Data public class DashboardQueryReqVO { @Pattern(regexp="SITE|PRODUCT") private String scope="SITE";@DateTimeFormat(pattern="yyyy-MM-dd") private LocalDate startDate;@DateTimeFormat(pattern="yyyy-MM-dd") private LocalDate endDate;private Boolean compare=false;private Long categoryId;private Long spuId; }
