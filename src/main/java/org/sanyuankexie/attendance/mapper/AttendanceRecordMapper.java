package org.sanyuankexie.attendance.mapper;

import org.apache.ibatis.annotations.*;
//import org.apache.ibatis.annotations.Mapper;
import org.sanyuankexie.attendance.common.DTO.RecordDTO;
import org.sanyuankexie.attendance.model.AttendanceRank;
import org.sanyuankexie.attendance.model.AttendanceRecord;

import java.util.List;

@Mapper
public interface AttendanceRecordMapper {

    @Insert("INSERT INTO attendance_record " +
            "(id, user_id, start, end, status, operator_id,term) " +
            "VALUES(#{id},#{userId}, #{start}, #{end}, #{status}, #{operatorId},#{term})")
    void insert(AttendanceRecord record);

    @Select("SELECT * FROM attendance_record " +
            "WHERE user_id=#{userId} AND status=#{status}")
    AttendanceRecord selectByUserIdAndStatus(@Param("userId") Long userId, @Param("status") int status);

    @Update("UPDATE attendance_record SET " +
            "end=#{end}, status=#{status}, operator_id=#{operatorId} " +
            "WHERE id=#{id}")
    void updateById(AttendanceRecord record);

    @Select("SELECT " +
            "r.user_id user_id, u.name user_name, u.dept user_dept, u.location user_location " +
            "FROM attendance_record r " +
            "LEFT JOIN user u ON u.id=r.user_id " +
            "WHERE r.status=1")
    List<RecordDTO> selectOnlineRecord();

    @Select("SELECT " +
            "r.user_id user_id, r.start start, r.end end, r.status status, " +
            "u.name user_name, u.dept user_dept, u.location user_location " +
            "FROM attendance_record r " +
            "LEFT JOIN user u ON u.id=r.user_id " +
            "WHERE r.user_id=#{userId} and r.term=#{term}" +
            "ORDER BY r.start DESC ")
    List<RecordDTO> selectRecordListByUserId(@Param("userId") Long userId,@Param("term") String term);

    @Select("select term from (select term from `attendance_record` where user_id=#{userId} group by term) as art order by term desc;\n")
    List<String> selectTerm(@Param("userId") Long userId);
//
//    @Select("select time from ")
}
