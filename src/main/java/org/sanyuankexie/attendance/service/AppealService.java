package org.sanyuankexie.attendance.service;

import lombok.extern.slf4j.Slf4j;
import org.sanyuankexie.attendance.common.DTO.AppealDealDTO;
import org.sanyuankexie.attendance.common.DTO.AppealQueryDTO;
import org.sanyuankexie.attendance.common.helper.PageResultHelper;
import org.sanyuankexie.attendance.mapper.AppealRecordMapper;
import org.sanyuankexie.attendance.mapper.UserMapper;
import org.sanyuankexie.attendance.model.AppealRecord;
import org.sanyuankexie.attendance.model.AppealRequest;
import org.sanyuankexie.attendance.model.SystemInfo;
import org.sanyuankexie.attendance.model.User;
import org.sanyuankexie.attendance.thread.EmailThread;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;

@Service
@Slf4j
public class AppealService {

    @Resource
    private SystemInfo systemInfo;

    @Resource
    private AppealRecordMapper appealRecordMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private UserService userService;

    @Resource
    private MailService mailService;

    @Resource
    ThreadPoolTaskExecutor threadPoolTaskExecutor;

    public AppealService(SystemInfo systemInfo) {
        this.systemInfo = systemInfo;
    }


    @Transactional
    public Object uploadAppeal(AppealRequest appealRequest) {
        AppealRecord appealRecord = new AppealRecord();
        long nowTime = System.currentTimeMillis();
        User user = appealRequest.getAppealUser();
        String thisRecordId = Long.toString(nowTime) + user.getId();
        appealRecord.setId(thisRecordId);
        appealRecord.setSignRecordId(appealRequest.getSignRecordId());
        appealRecord.setAppealUser(user);
        appealRecord.setRequireAddTime(appealRequest.getRequireAddTime());
        appealRecord.setReason(appealRequest.getReason());
        appealRecord.setAppealImageUrls(appealRequest.getAppealImageUrls());
        appealRecord.setAppealTime(nowTime);
        appealRecord.setStatus(0);
        appealRecord.setTerm(systemInfo.getTerm());
        System.out.print(appealRecord);
        appealRecordMapper.insert(appealRecord);
        sendMailRemindManager(user.getId());  // 发送邮件提醒对应正副部长及时处理该请求
        return thisRecordId;
    }


    public void sendMailRemindManager(long studentId) {
        User user = userMapper.selectByUserId(studentId);
        if (user == null) {
            throw new NullPointerException("user is null");
        }
        int department = mapDepartment(user.getDept());
        List<User> managers = userMapper.selectDepartmentManager(department);
        Long target = 5201314L;
        for (User manager : managers) {
            try {
                target = manager.getId();
                threadPoolTaskExecutor.execute(new EmailThread(mailService, target, "RemindManager.html", "[科协事务]: 有新的申诉需要处理", null));
                log.info("<System><{}>已发送提醒成功", target);
            } catch (Exception e) {
                log.error("<System><{}>事务提醒发生了一些错误", target);
            }
        }
    }

    /**
     * 获取申诉记录列表（带分页）
     *
     * @param appealQueryDTO 查询条件
     * @return 分页结果
     */
    public PageResultHelper<AppealRecord> getAppealList(AppealQueryDTO appealQueryDTO) {
        Integer pageNum = appealQueryDTO.getPageNum();
        Integer pageSize = appealQueryDTO.getPageSize();
        if (pageNum == null || pageNum <= 0) {
            pageNum = 1;  // 默认第一页
        }
        if (pageSize == null || pageSize <= 0) {
            pageSize = Integer.MAX_VALUE;  // 如果没有分页参数，则查询所有记录
        }
        // 计算偏移量
        int offset = (pageNum - 1) * pageSize;
        // 执行查询
        List<AppealRecord> records = appealRecordMapper.selectAppealRecords(
                appealQueryDTO.getAppealId(),
                appealQueryDTO.getName(),
                appealQueryDTO.getDepartment(),
                appealQueryDTO.getTerm(),
                appealQueryDTO.getStudentId(),
                appealQueryDTO.getStatus(),
                appealQueryDTO.getOperator(),
                pageSize,
                offset
        );
        long total = appealRecordMapper.countAppealRecords(
                appealQueryDTO.getAppealId(),
                appealQueryDTO.getName(),
                appealQueryDTO.getDepartment(),
                appealQueryDTO.getTerm(),
                appealQueryDTO.getStudentId(),
                appealQueryDTO.getStatus(),
                appealQueryDTO.getOperator()
        );
        return new PageResultHelper<>(
                records,
                total,
                pageNum,
                pageSize
        );
    }

    @Transactional
    public String dealAppeal(AppealDealDTO appealDealDTO) {
        // 查询当前申诉记录
        AppealRecord record = appealRecordMapper.selectById(appealDealDTO.getDealAppealId());
        if (record == null) {
            throw new IllegalArgumentException("未找到对应的申诉记录");
        }
        // 获取处理人信息
        User operator = new User();
        operator.setId(appealDealDTO.getOperatorId());
        // 更新记录
        record.setOperator(operator); // 处理人对象，只传 ID 即可
        record.setDealTime(System.currentTimeMillis()); // 处理时间
        record.setRealAddTime(appealDealDTO.getRealAddTime());
        // 执行增加时长
        if (appealDealDTO.getResult()) {
            record.setStatus(1); // 设置处理状态（如1=通过，2=驳回）
            userService.modifyTime(
                    "add",
                    record.getAppealUser().getId(),
                    appealDealDTO.getRealAddTime(),
                    systemInfo.getPassword(),
                    null
            );
        } else {
            record.setStatus(2);
            record.setFailedReason(appealDealDTO.getFailedReason());
        }
        appealRecordMapper.updateById(record); // 更新数据库
        sendMailRemindAppealer(record.getAppealUser().getId());
        return "处理成功";
    }

    public void sendMailRemindAppealer(long studentId) {
        try {
            threadPoolTaskExecutor.execute(new EmailThread(mailService, studentId, "RemindAppealer.html", "[科协事务]: 你的申诉已被处理", null));
            log.info("<System><{}>已发送提醒成功", studentId);
        } catch (Exception e) {
            log.error("<System><{}>事务提醒发生了一些错误", studentId);
        }
    }

    /**
     * 取得对应部门编号
     *
     * @param dept 部门名称
     * @return 部门编号，2是软件部，3是多媒体部，4是硬件部，5是安全部，1是主席团除三大部长外成员
     */
    int mapDepartment(String dept) {
        switch (dept) {
            case "软件部":
                return 2;
            case "多媒体部":
                return 3;
            case "硬件部":
                return 4;
            case "安全部":
                return 5;
            default:
                return 1;
        }
    }

}
