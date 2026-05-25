# QuadPipe：面向FPGA大模型推理的低误差高吞吐Softmax加速器

[![SpinalHDL](https://img.shields.io/badge/SpinalHDL-1.12.0-blue)](https://github.com/SpinalHDL/SpinalHDL)
[![Scala](https://img.shields.io/badge/Scala-2.13.14-red)](https://www.scala-lang.org/)
[![License](https://img.shields.io/badge/license-MIT-green)](LICENSE)

**QuadPipe** 是一款面向FPGA大模型推理的开源高吞吐Softmax硬件加速器。采用SpinalHDL实现，在轻量级资源占用下可达到每周期4个FP32结果的稳态吞吐，板级实测MAE维持在10⁻¹¹–10⁻¹⁰量级。

## 目录

- [概述](#概述)
- [核心特性](#核心特性)
- [系统架构](#系统架构)
- [流水线与数据流](#流水线与数据流)
- [性能指标](#性能指标)
- [工程结构](#工程结构)
- [环境准备](#环境准备)
- [快速开始](#快速开始)
- [配置说明](#配置说明)
- [验证体系](#验证体系)
- [引用](#引用)

## 概述

在Transformer架构的大模型推理中，Softmax已从分类层的辅助组件演变为核心算子：它作用于N×N的注意力得分矩阵，复杂度随序列长度O(N²)增长；分母需要全局归约，指数和除法均为高时延超越/迭代运算。在FPGA平台上实现高效Softmax面临三重瓶颈：

1. **长序列数值漂移**：向量维度扩展至数千时，传统FP32/FP16累加器在大量指数项求和时产生严重的舍入误差积累，乃至溢出。
2. **变长序列流水线气泡**：固定并行度流水线在处理非对齐向量尾拍时需要软件预处理或掩码控制，损害硬件有效利用率。
3. **访存带宽与计算密度失衡**：大批处理下，频繁的指令配置与非连续访存制约系统吞吐。

QuadPipe以三项设计协同应对上述挑战，在统一流水线框架内同时达成数值稳定性、变长输入处理与系统吞吐匹配——无需依赖软件侧的脆弱变通方案。

## 核心特性

### 1. Q48.47格式96位扩展精度累加

在分母求和通路引入Q48.47定点累加器（48位整数 + 47位小数，等效96位宽度），将单次累加舍入误差界从FP32的O(2⁻²⁴)压缩至O(2⁻⁴⁷)。全系统仅消耗8个DSP。

板级实测MAE在所有测试长度（N ∈ {128, 512, 1024, 2048}）上均维持在10⁻¹¹–10⁻¹⁰量级，与10⁻⁶工程阈值间存在约4个数量级的安全裕量。在N=2048时，分母误差较标准FP32累加降低87.59%，且MAE随N增长保持单调衰减而非触达精度地板。

### 2. 硬件级边界无关处理

利用e⁻∞ = 0的数学性质，在计算核心内部对非4对齐向量的尾拍无效槽位自动插入NEG_INF，使变长序列无需软件预填充即可进入统一计算通路。该机制消除了软件侧长度检测、数据重组与显式掩码控制的开销。

板级实测有效元素吞吐较软件预补齐方案最高提升6.49%（N=1025），批处理时延最高缩减5.98%。

### 3. X–Y双维批处理调度与跨精度数据路径

命令解析模块支持X（向量长度，最大1024）和Y（批量数，最大65,536）的一次性配置，将主机配置频率从逐向量降低至逐批次，消除高频调用下的配置瓶颈。

采用INT8加载、FP32核心计算、FP32写回的跨精度数据路径，在保证计算精度的同时将输入接口带宽需求压缩至FP32方案的1/4。

### 4. 多级反压闭环控制

自顶向下的反压链路统一了AXI输出接口、除法归一化级、片上缓存消费与输入馈送：输出端拥塞→Pass-2暂停→缓存消费减缓→馈送使能关闭→新输入被阻止，恢复时按相反方向逐级打开。系统仅在等待输出侧就绪时可能阻塞，不会形成内部死锁。

## 系统架构

QuadPipe按接口层、控制层、计算层与存储层四层结构组织。

![顶层分层架构](images/3-1.png)

**图1**　顶层分层与模块职责示意图。实线为数据通路（AXI读主 → 位宽转换 → Softmax计算核心 → 指数缓存/结果缓冲 → AXI写主），虚线为控制通路（AXI寄存器片 → 命令解析 → Softmax计算核心）。

### 接口层
- **AxiRdM**：AXI4突发读主模块，从DDR中读取INT8输入向量。
- **AxiWrM**：AXI4突发写主模块，将FP32结果写回DDR。
- **AxiRsSm**：AXI寄存器片模块，解耦结果缓冲与写主之间的时序。

### 控制层
- **CmdParM**：命令解析模块，将主机写入的`cmd_x_len`、`cmd_y_len`、缩放因子与基地址一次性转换为驱动数据面的向量生命周期信号（`x_cnt`、`last_x`、`last_y`）。
- **WidthConvert**：位宽转换模块，将每个128位AXI读拍（16个INT8元素）按32位字粒度顺序拆解，在连续4个周期内送入计算核心，使总线粒度与计算粒度精确对齐。

### 计算层
- **SoftmaxCalcM**：4路并行、6级深度流水的计算核心，覆盖反量化、INT8→FP32转换、指数近似、树形归约、扩展精度累加与归一化除法。

### 存储层
- **RamS2p1cSm**：指数缓存RAM（128b×1024），暂存Pass-1指数结果供Pass-2回读。
- **ResultBuf**：结果缓冲（128b×32），对齐除法输出与外部反压域。

## 流水线与数据流

### 两遍式数据流

Softmax的本质约束在于所有输出共享同一个全局分母S = Σeˣ，在分母未完成前无法给出任何归一化结果。QuadPipe采用两遍式策略复用单套指数单元：

![两遍式数据流](images/3-2.png)

**图2**　两遍式数据流与存储复用示意图。Pass-1（上支路）完成指数计算与分母累加，每拍4个指数结果一方面写入片上指数缓存，另一方面送入树形归约与Q48.47累加器；Pass-2（下支路）从缓存中顺序回读指数值，利用已就绪的全局分母执行4路并行归一化除法。

该设计以适度BRAM开销（共14个BRAM）换取指数单元数量的减半和流水线的规则化组织。

### 6级深流水管线

![流水线阶段](images/3-3.png)

**图3**　计算核心流水线阶段与时延分配。S1–S6六个顺序流水阶段分别完成反量化缩放、定点→浮点转换、指数近似、4路归约、跨周期累加与归一化除法，对应时延2、5、14、14、8、16周期。累计首结果时延59周期，流水线填满后稳态吞吐达到每周期4个FP32结果。

图3底部的指数缓存旁路回路以S3与S6为两端，构建了写入–回读闭环，将系统级两遍式数据流压缩于同一计算核心内部。

### 反压控制闭环

![反压链路](images/3-4.png)

**图4**　反压链路与控制信号传播示意图。箭头颜色指示压力方向：红色为外部反压，橙色为缓冲释放，绿色为上游许可。该链路以`data_o_ready`为源头，依次门控除法状态机、限流缓存消费、最终控制输入端`data_i_ready`，形成单一闭环。

## 性能指标

### 资源占用（Xilinx Kintex-7 325T）

| 资源 | 用量 | 占比 |
|---|---|---|
| LUT | 9,455 | 4.72% |
| FF | 7,453 | 1.82% |
| DSP | 8 | 0.95% |
| BRAM | 14 | 3.15% |

### 吞吐与时延

| 指标 | 数值 |
|---|---|
| 最大频率（时序收敛） | 150 MHz |
| 首结果时延 | 59周期 |
| 稳态吞吐 | 4元素/周期 |
| 峰值吞吐 | 600M elements/s |
| 最大向量长度X | 1,024 |
| 最大批处理数Y | 65,536 |

### 数值精度（相对FP64参考）

| 序列长度N | RTL MAE均值 | RTL MAE最大值 | 通过10⁻⁶阈值 |
|---|---|---|---|
| 128 | 2.92×10⁻¹⁰ | 3.56×10⁻¹⁰ | 是 |
| 512 | 1.14×10⁻¹⁰ | 1.56×10⁻¹⁰ | 是 |
| 1024 | 4.32×10⁻¹¹ | 5.61×10⁻¹¹ | 是 |
| 2048 | 2.47×10⁻¹¹ | 3.34×10⁻¹¹ | 是 |

> 在N=2048时，Q48.47累加使分母误差较标准FP32累加降低**87.59%**，且MAE随N增长保持单调衰减，呈现"长序列受益递增"特征。

### 变长处理效率（hw_auto_pad vs sw_pre_pad）

| 长度N | 吞吐提升 | 时延缩减 |
|---|---|---|
| 127 | — | — |
| 511 | +5.96% | −5.47% |
| 1025 | **+6.49%** | **−5.98%** |
| 2049 | +0.76% | −0.79% |

> 硬件边界无关处理在中长向量上一致占优。增益曲线呈倒U形，在N=1025处达到峰值（此时填充开销相对有效计算的比例最大）。

## 工程结构

```
QuadPipe/
├── build.sbt                          # SBT构建定义
├── build.sc                           # Mill构建定义（备用）
├── hw/
│   ├── spinal/
│   │   └── softmax/
│   │       ├── QuadPipe.scala         # 顶层集成模块
│   │       ├── QuadPipeCocotbGen.scala # 参数化Verilog生成入口
│   │       ├── axi/                   # AXI接口模块
│   │       │   ├── AxiRdM.scala       #   AXI4读主（DDR突发读）
│   │       │   ├── AxiWrM.scala       #   AXI4写主（DDR突发写）
│   │       │   ├── AxiRsSm.scala      #   AXI寄存器片（时序解耦）
│   │       │   └── CmdParM.scala      #   命令解析（X-Y批处理调度）
│   │       ├── core/                  # 计算核心模块
│   │       │   ├── SoftmaxCalcM.scala #   Softmax计算核心（6级流水线）
│   │       │   ├── WidthConvert.scala #   128b→32b位宽转换
│   │       │   ├── RamS2p1cSm.scala   #   指数缓存RAM（128b×1024）
│   │       │   └── ResultBuf.scala    #   结果缓冲（128b×32）
│   │       ├── fp/                    # 浮点运算单元
│   │       │   ├── FltCompat.scala    #   兼容封装（FltConvert/FltExp/FltAdd/FltAcc/FltDiv）
│   │       │   ├── FltExpWrapper.scala#   指数近似（查表+多项式）
│   │       │   ├── FltExpCcm.scala    #   指数查表/系数模块
│   │       │   ├── FltExpE2a.scala    #   指数中间变换
│   │       │   ├── FltExpRecomb.scala #   指数重组
│   │       │   ├── FltExpSpecialcase.scala # 指数特殊值处理
│   │       │   ├── FltAccum.scala     #   Q48.47扩展精度累加核心
│   │       │   ├── FltAccumFltToFix.scala  # 浮点→定点累加转换
│   │       │   ├── FltAddSubLogic.scala    # 加减统一逻辑
│   │       │   ├── FltAlignAdd.scala  #   对齐加法
│   │       │   ├── FltAlignment.scala #   指数对齐控制
│   │       │   ├── FltDivWrapper.scala#   除法包装器（归一化阶段）
│   │       │   ├── FltDivMant.scala   #   除法尾数路径
│   │       │   ├── FltFixToFltConv.scala   # 定点→FP32转换
│   │       │   ├── FltToFixConv.scala #   FP32→定点转换
│   │       │   ├── FltDsp48e2Wrapper.scala # DSP48E2原语包装器
│   │       │   └── FltDsp48e1Wrapper.scala # DSP48E1原语包装器
│   │       └── util/                  # 工具模块
│   │           ├── FltDelay.scala     #   通用延迟链
│   │           ├── FltMux4.scala      #   4路选择器
│   │           ├── FltShiftMsbFirst.scala  # MSB优先移位器
│   │           ├── FltLeadZeroEncode.scala # 前导零编码
│   │           ├── FltRenormAndRoundLogic.scala # 重规格化与舍入
│   │           ├── FltRoundBit.scala  #   舍入位计算
│   │           ├── FltSpecialDetect.scala   # 特殊值检测
│   │           └── FltAccumBitEncode.scala  # 累加位编码
│   ├── spinal_test/
│   │   └── softmax/                   # ScalaTest单元/集成测试
│   ├── verilog/                       # 参考Verilog源文件
│   ├── vhdl/                          # 参考VHDL源文件
│   └── gen/                           # 生成的Verilog输出目录
├── images/                            # 架构示意图
│   ├── 3-1.png                        #   顶层分层架构
│   ├── 3-2.png                        #   两遍式数据流
│   ├── 3-3.png                        #   计算核心流水线阶段
│   └── 3-4.png                        #   反压控制链路
└── project/                           # SBT插件配置
```

## 环境准备

| 工具 | 版本 | 说明 |
|---|---|---|
| JDK | 17+ | |
| SBT | 1.10.x | 主构建工具 |
| Scala | 2.13.14 | 由SBT管理 |
| SpinalHDL | 1.12.0 | 在`build.sbt`中声明 |

可选工具（用于仿真与FPGA实现）：

| 工具 | 用途 |
|---|---|
| Verilator | Cocotb RTL仿真 |
| Python 3.8+ (含cocotb) | 协同仿真测试平台 |
| Vivado | 综合、布局布线、比特流生成 |

## 快速开始

### 1. 克隆仓库

```bash
git clone <仓库地址>
cd QuadPipe
```

### 2. 编译

```bash
sbt compile
```

此命令编译`hw/spinal/softmax/`下全部SpinalHDL源码。

### 3. 生成Verilog

将可综合的Verilog网表输出至`hw/gen/QuadPipe.v`：

```bash
sbt "runMain softmax.QuadPipe"
```

如需自定义输出目录和文件名：

```bash
sbt "runMain softmax.QuadPipeCocotbGen <输出目录> <文件名.v>"
```

示例：

```bash
sbt "runMain softmax.QuadPipeCocotbGen /tmp/quadpipe_out QuadPipe.v"
```

### 4. 运行单元测试

```bash
sbt test
```

此命令运行全部基于ScalaTest的单元测试与集成测试，覆盖浮点模块、AXI接口、计算核心与控制逻辑。

## 配置说明

QuadPipe在elaboration阶段通过`QuadPipe`类的SpinalHDL参数进行配置：

| 参数 | 默认值 | 说明 |
|---|---|---|
| `axiIdWidth` | 6 | AXI ID信号位宽 |
| `axiIdLoadId` | 0 | AXI读通道ID |
| `axiIdSaveId` | 0 | AXI写通道ID |
| `axiAddrWidth` | 32 | AXI地址总线位宽 |
| `axiLenWidth` | 8 | AXI突发长度位宽 |
| `axiDataWidth` | 128 | AXI数据总线位宽 |
| `sfmDsp48Ver` | `"DSP48E2"` | DSP原语版本（`"DSP48E1"`或`"DSP48E2"`） |

运行时通过寄存器接口控制：

| 寄存器 | 位宽 | 说明 |
|---|---|---|
| `reg_sm_cmd_x_len` | 12 | 向量长度（每个向量的元素数N） |
| `reg_sm_cmd_y_len` | 16 | 批处理数量（向量个数） |
| `reg_sm_cmd_scale` | 5 | 反量化缩放因子s（建议s ≥ 4） |
| `reg_sm_cmd_offset` | 32 | 输入偏置值 |
| `reg_sm_cmd_src_addr` | 32 | INT8输入数据的AXI源地址 |
| `reg_sm_cmd_dst_addr` | 32 | FP32输出数据的AXI目标地址 |
| `reg_sm_cmd_valid` | 1 | 命令触发（脉冲写入以启动批次） |
| `reg_sm_cmd_done` | 1 | 状态：批次完成（只读） |
| `reg_sm_cmd_done_clr` | 1 | 清除完成标志 |

**典型使用流程**：
1. 写入`cmd_x_len`、`cmd_y_len`、`cmd_scale`、`cmd_offset`、`cmd_src_addr`、`cmd_dst_addr`。
2. 脉冲置位`cmd_valid` = 1。
3. 轮询`cmd_done`直至断言。
4. 脉冲置位`cmd_done_clr`以确认。

## 验证体系

QuadPipe采用多层验证策略：

### 单元测试（ScalaTest）

- **浮点模块测试**（`SoftmaxFpUtilSelfSpec`）：24项测试覆盖FltDelay、FltMux4、FltRoundBit、FltSpecialDetect、FltAccum、FltAddExp、FltAddSubLogic、FltAlignment、FltAlignAdd、FltDivExp、FltExpCcm、FltExpE2a、FltExpRecomb、FltExpSpecialcase、FltExpWrapper、FltDivWrapper等。
- **收敛测试**（`SoftmaxConvergenceSpec`）：FltConvert、FltExp、FltAcc相对参考模型的收敛性验证。
- **系统级测试**（`SoftmaxRefactorSpec`）：CmdParM、WidthConvert、AxiWrM、SoftmaxCalcM、QuadPipe顶层测试。

运行全部测试：

```bash
sbt test
```

### Cocotb协同仿真

基于cocotb的逐拍差分验证环境可对QuadPipe整体与参考模型进行周期精确的对比验证。详见cocotb目录下基于Makefile的仿真流程。

## 引用

若您在研究中使用了QuadPipe，请引用：

```bibtex
@article{quadpipe2025,
  title     = {QuadPipe: A Low-Error and High-Throughput Softmax Accelerator
               for Large Model Inference on FPGA},
  author    = {},
  journal   = {},
  year      = {2025},
}
```

## 许可证

本项目基于MIT许可证分发。详见[LICENSE](LICENSE)文件。
