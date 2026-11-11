<template>
  <div class="houseRenting">
    <el-form :inline="true" :model="searchRenting" class="searchRenting" size="small">
      <el-form-item label="房源编号">
        <el-input v-model="searchRenting.houseId" clearable placeholder="请输入房源编号"></el-input>
      </el-form-item>
      <el-button type="primary" size="small" @click="findRentingPage">搜索</el-button>
    </el-form>
    <el-table :data="rentingList" :header-cell-style="{
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
      <el-table-column prop="id" label="在租编号" width="150px" show-overflow-tooltip sortable></el-table-column>
      <el-table-column prop="houseId" label="房源编号" width="180px" show-overflow-tooltip></el-table-column>
      <el-table-column prop="tenantName" label="租客"></el-table-column>
      <el-table-column prop="money" label="租金(元/月)" sortable></el-table-column>
      <el-table-column prop="startTime" label="起租时间" sortable></el-table-column>
      <el-table-column prop="endTime" label="到期时间" sortable></el-table-column>
      <el-table-column label="合同">
        <template v-slot="scope">
          <el-link v-if="scope.row.contract" type="primary" :href="baseUrl + scope.row.contract" target="_blank">查看合同</el-link>
          <span v-else>未上传</span>
        </template>
      </el-table-column>
      <el-table-column prop="createDate" label="创建时间" sortable></el-table-column>
      <el-table-column label="操作" width="100px">
        <template v-slot="scope">
          <el-button size="mini" type="warning" icon="el-icon-delete" @click="deleteRenting(scope.row.id)"></el-button>
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
      :total="rentingPage.count">
    </el-pagination>
  </div>
</template>

<script>
import { base } from "../../config";

export default {
  name: "HouseRenting",
  data() {
    return {
      baseUrl: base.picLocal,
      searchRenting: {
        houseId: ""
      },
      rentingPage: {
        count: 0,
        pageSize: 10,
        pageNo: 1
      },
      rentingList: []
    };
  },
  methods: {
    sizeChange(val) {
      this.rentingPage.pageSize = val;
      this.findRentingPage();
    },
    currentChange(val) {
      this.rentingPage.pageNo = val;
      this.findRentingPage();
    },
    tableRowClassName({ row, rowIndex }) {
      let index = rowIndex;
      if (index % 2 == 0) {
        return "warning-row";
      } else {
        return "warning-col";
      }
    },
    findRentingPage() {
      //在租分页接口通过表单参数接收查询条件
      let params = {
        pageNo: this.rentingPage.pageNo,
        pageSize: this.rentingPage.pageSize,
        houseId: this.searchRenting.houseId
      };
      this.$network
        .findRentingListPage(params)
        .then(res => {
          if (res.data.code == "200") {
            this.rentingList = res.data.data.list;
            this.rentingPage.count = res.data.data.count;
          }
        })
        .catch(err => {
          console.log(err);
        });
    },
    deleteRenting(id) {
      this.$confirm("是否删除该在租记录?", "警告", {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning"
      })
        .then(() => {
          this.$network.deleteRenting({ id: id }).then(res => {
            if (res.data.code == "200") {
              this.$message.success(res.data.message);
              this.findRentingPage();
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
    //发送请求，查询在租列表
    this.findRentingPage();
  }
};
</script>

<style scoped>
.searchRenting {
  float: left;
}
</style>
