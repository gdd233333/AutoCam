# 数据集下载链接（学术 / 非商用）

本地目录：`ml/datasets/raw/`（gitignore，不进 git）。

## 已直链下好（本机 `D:\AutoCam\ml\datasets\raw\`）

| 集 | 本地 | 数量 |
|----|------|------|
| **CADB** 图+构图标注 | `raw/cadb/CADB_Dataset/` | 9497 图 + json |
| ECSSD 图+mask | `raw/ecssd/` | 1000+1000 |
| CSSD 图+mask | `raw/cssd/` | 200+200 |
| DUTS-TR + DUTS-TE | `raw/duts/` | 训练+测试，约 3.1 万文件 |
| DUT-OMRON 图 | `raw/dut-omron/DUT-OMRON-image/` | 约 5168 图（GT zip 官网 404） |
| MSRA10K 图+mask | `raw/msra10k/MSRA10K_Imgs_GT/` | 1 万图+1 万 mask |
| PICD 标签 | `raw/picd/labels_PICD.csv` | 标注表，图要网盘 |

直链（可再下）：

```
https://www.cse.cuhk.edu.hk/leojia/projects/hsaliency/data/ECSSD/images.zip
https://www.cse.cuhk.edu.hk/leojia/projects/hsaliency/data/ECSSD/ground_truth_mask.zip
https://www.cse.cuhk.edu.hk/leojia/projects/hsaliency/data/CSSD/images.zip
https://www.cse.cuhk.edu.hk/leojia/projects/hsaliency/data/CSSD/ground_truth_mask.zip
http://saliencydetection.net/duts/download/DUTS-TR.zip
http://saliencydetection.net/duts/download/DUTS-TE.zip
http://saliencydetection.net/dut-omron/download/DUT-OMRON-image.zip
http://mftp.mmcheng.net/Data/MSRA10K_Imgs_GT.zip
https://raw.githubusercontent.com/CV-xueba/PICD_ImageComposition/main/labels_PICD.csv
https://raw.githubusercontent.com/CV-xueba/PICD_ImageComposition/main/image_link_public.csv
```

## 要你自己点的（网盘，没法稳定直链）

**PICD 构图主集（CC BY-NC-SA 4.0，非商用 OK）**

- 图 Part1（44,577 张可再分发）：[Google Drive 文件夹](https://drive.google.com/drive/folders/10MyHEtoOj61n7cIC6jAMZOXK7K4hgv4e?usp=drive_link)
- 百度网盘：https://pan.baidu.com/s/17dWynHJzCTi3fe5dy0v8Fw 提取码 **1517**
- 标签：已下到 `raw/picd/labels_PICD.csv`
- 条款：https://github.com/CV-xueba/PICD_ImageComposition/blob/main/PICD_Dataset_Terms_of_Use.pdf
- 解压后放到 `ml/datasets/raw/picd/`

**CADB**：已经下完，在 `raw/cadb/CADB_Dataset/`。备份链接：

- Dropbox：https://www.dropbox.com/scl/fi/fvlsnit7on6218szply4q/CADB_Dataset.zip?rlkey=mwt9eftdhmnawomv44x4deliw&dl=0
- 百度：https://pan.baidu.com/s/1MrOSj35re84dLLNk72CLQQ?pwd=xkar

## 接到训练

显著性集没有构图类，mask 转框后用 CLIP 或启发式：

```powershell
python ml/datasets/prepare.py --folder D:\AutoCam\ml\datasets\raw\ecssd
python ml/teachers/run_teachers.py --index ml/datasets/index.jsonl --device cuda
```

PICD/CADB 有构图类后再 `--picd` / `--cadb`。
