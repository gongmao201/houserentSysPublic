<template>
  <div class="houseManage">
    <el-form :inline="true" :model="searchHouse" class="searchHouse" size="small">
      <el-form-item label="状态">
        <el-select v-model="searchHouse.state" placeholder="房源状态" clearable>
          <el-option label="审核中" :value="0"></el-option>
          <el-option label="已发布" :value="1"></el-option>
          <el-option label="已出租" :value="2"></el-option>
        </el-select>
      </el-form-item>
      <el-form-item label="检索条件">
        <el-input v-model="searchHouse.condition" clearable placeholder="请输入地址或房源编号"></el-input>
      </el-form-item>
      <el-button type="primary" size="small" @click="findHousePage">搜索</el-button>
      <el-button type="primary" size="small" @click="addHouse">发布房源</el-button>
    </el-form>
    <el-table :data="houseList" :header-cell-style="{
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
      <el-table-column prop="id" label="房源编号" width="180px" show-overflow-tooltip sortable></el-table-column>
      <el-table-column label="所在地区">
        <template v-slot="scope">
          {{ scope.row.province + scope.row.city + scope.row.county }}
        </template>
      </el-table-column>
      <el-table-column prop="address" label="详细地址" show-overflow-tooltip></el-table-column>
      <el-table-column prop="money" label="租金(元/月)" sortable></el-table-column>
      <el-table-column prop="area" label="面积(㎡)" sortable></el-table-column>
      <el-table-column prop="houseTypeStr" label="户型"></el-table-column>
      <el-table-column prop="floor" label="楼层" sortable></el-table-column>
      <el-table-column label="出租类型">
        <template v-slot="scope">
          {{ scope.row.rentType == 0 ? "整租" : "合租" }}
        </template>
      </el-table-column>
      <el-table-column prop="state" label="状态" sortable>
        <template v-slot="scope">
          <el-tag v-if="scope.row.state == 0" type="warning">审核中</el-tag>
          <el-tag v-if="scope.row.state == 1" type="success">已发布</el-tag>
          <el-tag v-if="scope.row.state == 2" type="info">已出租</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="房东">
        <template v-slot="scope">
          {{ scope.row.createBy == null ? "" : scope.row.createBy.name }}
        </template>
      </el-table-column>
      <el-table-column prop="createDate" label="创建时间" sortable></el-table-column>
      <el-table-column label="操作" width="210px">
        <template v-slot="scope">
          <el-button size="mini" type="success" v-if="scope.row.state == 0" @click="adoptHouse(scope.row)">通过审核</el-button>
          <el-button size="mini" type="primary" icon="el-icon-edit" @click="edit(scope.row)"></el-button>
          <el-button size="mini" type="warning" icon="el-icon-delete" @click="deleteHouse(scope.row.id)"></el-button>
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
      :total="housePage.count">
    </el-pagination>

    <el-dialog :visible.sync="houseDialogVisible" :title="houseEdit.id ? '修改房源' : '发布房源'" width="50%">
      <el-form :model="houseEdit" :rules="rules" ref="houseEdit" size="small" label-width="100px">
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="省份：" class="is-required" prop="province">
              <el-input v-model="houseEdit.province" placeholder="例如：四川省"></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="城市：" class="is-required" prop="city">
              <el-input v-model="houseEdit.city" placeholder="例如：成都市"></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="区/县：" class="is-required" prop="county">
              <el-input v-model="houseEdit.county" placeholder="例如：武侯区"></el-input>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="详细地址：" class="is-required" prop="address">
          <el-input v-model="houseEdit.address" placeholder="请输入详细地址"></el-input>
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="租金(元/月)：" class="is-required" prop="money">
              <el-input v-model="houseEdit.money" placeholder="请输入租金"></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="面积(㎡)：" class="is-required" prop="area">
              <el-input v-model="houseEdit.area" placeholder="请输入面积"></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="户型：" class="is-required" prop="houseType">
              <el-select v-model="houseEdit.houseType" placeholder="选择户型">
                <el-option v-for="item in houseTypeList" :label="item.label" :value="item.value" :key="item.id"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="楼层：" prop="floor">
              <el-input v-model="houseEdit.floor" placeholder="请输入楼层"></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="修建时间：" prop="buildTime">
              <el-input v-model="houseEdit.buildTime" placeholder="例如：2015"></el-input>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="出租类型：" prop="rentType">
              <el-select v-model="houseEdit.rentType" placeholder="选择出租类型">
                <el-option label="整租" :value="0"></el-option>
                <el-option label="合租" :value="1"></el-option>
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="合租人数：" prop="peopleNum">
          <el-input v-model="houseEdit.peopleNum" placeholder="整租时填1"></el-input>
        </el-form-item>
        <el-form-item label="房源描述：" prop="describe">
          <el-input type="textarea" :rows="3" v-model="houseEdit.describe" placeholder="请输入房源描述"></el-input>
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button size="small" @click="cancelEdit">取 消</el-button>
        <el-button size="small" type="primary" @click="saveHouseForm('houseEdit')">保 存</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
export default {
  name: "HouseManage",
  data() {
    return {
      searchHouse: {
        state: null,
        condition: ""
      },
      housePage: {
        count: 0,
        pageSize: 10,
        pageNo: 1
      },
      houseList: [],
      houseTypeList: [],
      houseDialogVisible: false,
      houseEdit: {
        id: "",
        province: "",
        city: "",
        county: "",
        address: "",
        money: "",
        area: "",
        houseType: "",
        floor: "",
        buildTime: "",
        rentType: 0,
        peopleNum: 1,
        describe: ""
      },
      rules: {
        province: [{ required: true, message: "请输入省份", trigger: "blur" }],
        city: [{ required: true, message: "请输入城市", trigger: "blur" }],
        county: [{ required: true, message: "请输入区/县", trigger: "blur" }],
        address: [{ required: true, message: "请输入详细地址", trigger: "blur" }],
        money: [{ required: true, message: "请输入租金", trigger: "blur" }],
        area: [{ required: true, message: "请输入面积", trigger: "blur" }],
        houseType: [{ required: true, message: "请选择户型", trigger: "change" }]
      }
    };
  },
  methods: {
    sizeChange(val) {
      this.housePage.pageSize = val;
      this.findHousePage();
    },
    currentChange(val) {
      this.housePage.pageNo = val;
      this.findHousePage();
    },
    tableRowClassName({ row, rowIndex }) {
      let index = rowIndex;
      if (index % 2 == 0) {
        return "warning-row";
      } else {
        return "warning-col";
      }
    },
    findHousePage() {
      let params = {
        pageNo: this.housePage.pageNo,
        pageSize: this.housePage.pageSize,
        condition: this.searchHouse.condition
      };
      if (this.searchHouse.state != null) {
        params["state"] = this.searchHouse.state;
      }
      //房源分页接口接收 JSON 请求体
      this.$network
        .findHousePageJson(params)
        .then(res => {
          if (res.data.code == "200") {
            this.houseList = res.data.data.list;
            this.housePage.count = res.data.data.count;
          }
        })
        .catch(err => {
          console.log(err);
        });
    },
    addHouse() {
      //重置表单后打开新增弹窗，新增的房源由后端置为审核中
      this.houseEdit = {
        id: "",
        province: "",
        city: "",
        county: "",
        address: "",
        money: "",
        area: "",
        houseType: "",
        floor: "",
        buildTime: "",
        rentType: 0,
        peopleNum: 1,
        describe: ""
      };
      this.houseDialogVisible = true;
    },
    edit(row) {
      this.houseEdit = {
        id: row.id,
        province: row.province,
        city: row.city,
        county: row.county,
        address: row.address,
        money: row.money,
        area: row.area,
        houseType: row.houseType,
        floor: row.floor,
        buildTime: row.buildTime,
        rentType: row.rentType,
        peopleNum: row.peopleNum,
        describe: row.describe
      };
      this.houseDialogVisible = true;
    },
    cancelEdit() {
      this.houseDialogVisible = false;
    },
    saveHouseForm(formName) {
      this.$refs[formName].validate(valid => {
        if (valid) {
          let params = {
            province: this.houseEdit.province,
            city: this.houseEdit.city,
            county: this.houseEdit.county,
            address: this.houseEdit.address,
            money: Number(this.houseEdit.money),
            area: Number(this.houseEdit.area),
            houseType: this.houseEdit.houseType,
            floor: Number(this.houseEdit.floor),
            buildTime: this.houseEdit.buildTime,
            rentType: Number(this.houseEdit.rentType),
            peopleNum: Number(this.houseEdit.peopleNum),
            describe: this.houseEdit.describe,
            //后端保存房源时遍历图片与标签集合，传空数组避免空指针
            picList: [],
            tagList: []
          };
          if (this.houseEdit.id) {
            params.id = this.houseEdit.id;
          }
          this.$network
            .saveHouse(params)
            .then(res => {
              if (res.data.code == "200") {
                this.$message.success(res.data.message);
                this.houseDialogVisible = false;
                this.findHousePage();
              } else {
                this.$message.error(res.data.message);
              }
            })
            .catch(err => {
              console.log(err);
            });
        } else {
          return false;
        }
      });
    },
    adoptHouse(row) {
      this.$confirm("确定通过该房源的审核吗？", "提示", {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning"
      })
        .then(() => {
          //将房源状态修改为1（已发布）
          this.$network
            .saveHouse({ id: row.id, state: 1 })
            .then(res => {
              if (res.data.code == "200") {
                this.$message.success("审核通过");
                this.findHousePage();
              }
            })
            .catch(err => {
              console.log(err);
            });
        })
        .catch(() => {
          this.$message({
            type: "info",
            message: "已取消操作"
          });
        });
    },
    deleteHouse(id) {
      this.$confirm("是否删除该房源?", "警告", {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning"
      })
        .then(() => {
          this.$network.deleteHouse({ id: id }).then(res => {
            if (res.data.code == "200") {
              this.$message.success(res.data.message);
              this.findHousePage();
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
    //查询房源列表与户型字典
    this.findHousePage();
    this.$network.findAllJson({ type: "house_type" }).then(res => {
      if (res.data.code == "200") {
        this.houseTypeList = res.data.data;
      }
    });
  }
};
</script>

<style scoped>
.searchHouse {
  float: left;
}
</style>
