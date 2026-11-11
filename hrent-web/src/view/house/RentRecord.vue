<template>
  <div class="rentRecord">
    <el-form :inline="true" :model="searchRent" class="searchRent" size="small">
      <el-form-item label="状态">
        <el-select v-model="searchRent.state" placeholder="租金状态" clearable>
          <el-option label="未到时间" :value="0"></el-option>
          <el-option label="已到时间" :value="1"></el-option>
          <el-option label="已交租" :value="2"></el-option>
        </el-select>
      </el-form-item>
      <el-form-item label="在租编号">
        <el-input v-model="searchRent.rentingId" clearable placeholder="请输入在租编号"></el-input>
      </el-form-item>
      <el-button type="primary" size="small" @click="findRentPage">搜索</el-button>
    </el-form>
    <el-table :data="rentList" :header-cell-style="{
                'background-color': '#E8E8E8',
                'height': '3.56vw',
                'padding':'0',
                'border': 'none',
                'font-size':'0.7vw',
                'color':'#666666'
            }" height="600"
            :row-class-name="tableRowClassName"
            style="width: 100%;overflow: scroll;overflow-x: hidden !important;">
      <el-table-column type="index" label="序号"></el-table-column>
      <el-table-column prop="id" label="记录编号" width="150px" show-overflow-tooltip sortable></el-table-column>
      <el-table-column prop="rentingId" label="在租编号" width="150px" show-overflow-tooltip></el-table-column>
      <el-table-column prop="tenantName" label="租客"></el-table-column>
      <el-table-column prop="payableTime" label="应缴日期" sortable></el-table-column>
      <el-table-column prop="payTime" label="实缴日期" sortable></el-table-column>
      <el-table-column prop="state" label="状态" sortable>
        <template v-slot="scope">
          <el-tag v-if="scope.row.state == 0">未到时间</el-tag>
          <el-tag v-if="scope.row.state == 1" type="warning">已到时间</el-tag>
          <el-tag v-if="scope.row.state == 2" type="success">已交租</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createDate" label="创建时间" sortable></el-table-column>
      <el-table-column label="操作" width="100px">
        <template v-slot="scope">
          <el-button size="mini" type="warning" icon="el-icon-delete" @click="deleteRent(scope.row.id)"></el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      @size-change="sizeChange"
      @current-change="currentChange"
      background
      layout="total,sizes,prev, pager, next"
      :page-sizes="[10, 20, 30, 40]"
      :page-size="10"
      :total="rentPage.count">
    </el-pagination>
  </div>
</template>

<script>
export default {
  name: "RentRecord",
  data() {
    return {
      searchRent: {
        state: null,
        rentingId: ""
      },
      rentPage: {
        count: 0,
        pageSize: 10,
        pageNo: 1
      },
      rentList: []
    };
  },
  methods: {
    sizeChange(val) {
      this.rentPage.pageSize = val;
      this.findRentPage();
    },
    currentChange(val) {
      this.rentPage.pageNo = val;
      this.findRentPage();
    },
    tableRowClassName({ row, rowIndex }) {
      let index = rowIndex;
      if (index % 2 == 0) {
        return "warning-row";
      } else {
        return "warning-col";
      }
    },
    findRentPage() {
      //租金记录分页接口通过表单参数接收查询条件
      let params = {
        pageNo: this.rentPage.pageNo,
        pageSize: this.rentPage.pageSize,
        rentingId: this.searchRent.rentingId
      };
      if (this.searchRent.state != null) {
        params["state"] = this.searchRent.state;
      }
      this.$network
        .findRentPage(params)
        .then(res => {
          if (res.data.code == "200") {
            this.rentList = res.data.data.list;
            this.rentPage.count = res.data.data.count;
          }
        })
        .catch(err => {
          console.log(err);
        });
    },
    deleteRent(id) {
      this.$confirm("是否删除该租金记录?", "警告", {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning"
      })
        .then(() => {
          this.$network.deleteRent({ id: id }).then(res => {
            if (res.data.code == "200") {
              this.$message.success(res.data.message);
              this.findRentPage();
            } else {
              this.$message.error(res.data.message);
            }
          });
        })
        .catch(() => {
          this.$message({
            type: "info",
            message: "已取消删除"
          });
        });
    }
  },
  mounted() {
    //发送请求，查询租金记录列表
    this.findRentPage();
  }
};
</script>

<style scoped>
.searchRent {
  float: left;
}
</style>
