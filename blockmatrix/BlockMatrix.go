package blockmatrix

import (
  "fmt"
  "log"
  "bytes"
  "math"
  "crypto/sha256"
  "crypto/sha512"
  "crypto/rand"
)
// https://csrc.nist.gov/publications/detail/white-paper/2018/05/31/data-structure-for-integrity-protection-with-erasure-capability/draft

type BlockMatrix struct {

  Dimension int
  HashAlgorithm string
  BlockData [][]bytes.Buffer
  BlockHashes [][]string
  RowHashes []string
  HashOfRows string
  ColumnHashes []string
  HashOfColumns string
  HashOfMatrix string
}

var TraceEnabled bool = false

const (

  Sha256 = "SHA256"
  Sha384 = "SHA384"
  Sha512 = "SHA512"

  RandomBlockLength = 64
)

func Create( Dimension int, HashAlgorithm string ) *BlockMatrix {

  if TraceEnabled { log.Printf("Create(%d, %s) called\n", Dimension, HashAlgorithm) }

  bm := new(BlockMatrix)
  bm.Dimension = Dimension
  bm.HashAlgorithm = HashAlgorithm
  bm.BlockData = make([][]bytes.Buffer, Dimension)
  for i := 0; i < Dimension; i++ { bm.BlockData[i] = make([]bytes.Buffer, Dimension) }

  bm.BlockHashes = make([][]string, Dimension)
  for i := 0; i < Dimension; i++ { bm.BlockHashes[i] = make([]string, Dimension) }

  bm.RowHashes  = make([]string, Dimension)
  bm.ColumnHashes  = make([]string, Dimension)

  bm.fillDiagonalWithRandomData()

  if TraceEnabled { log.Printf("Create() returning %v\n", &bm) }

  return bm
}

func (bm *BlockMatrix) hashOfBytes( Data bytes.Buffer ) string {

  var HashStr string

  HashStr = ""

  if bm.HashAlgorithm == Sha256 {

    HashStr = fmt.Sprintf("%x", sha256.Sum256(Data.Bytes()))
  } else if bm.HashAlgorithm == Sha384 {

    HashStr = fmt.Sprintf("%x", sha512.Sum384(Data.Bytes()))
  } else if bm.HashAlgorithm == Sha512 {

    HashStr = fmt.Sprintf("%x", sha512.Sum512(Data.Bytes()))
  }

  return HashStr
}

func (bm *BlockMatrix) hashOfString( DataStr string ) string {

  var HashStr string

  HashStr = ""

  if bm.HashAlgorithm == Sha256 {

    HashStr = fmt.Sprintf("%x", sha256.Sum256([]byte(DataStr)))
  } else if bm.HashAlgorithm == Sha384 {

    HashStr = fmt.Sprintf("%x", sha512.Sum384([]byte(DataStr)))
  } else if bm.HashAlgorithm == Sha512 {

    HashStr = fmt.Sprintf("%x", sha512.Sum512([]byte(DataStr)))
  }

  return HashStr
}

func (bm *BlockMatrix) updateHashOfRows() {

  var Hashes string

  Hashes = ""
  for i := 0; i < bm.Dimension; i++ {

    Hashes += bm.RowHashes[i]
  }

  bm.HashOfRows = bm.hashOfString(Hashes)
}

func (bm *BlockMatrix) updateHashOfColumns() {

  var Hashes string

  Hashes = ""
  for j := 0; j < bm.Dimension; j++ {

    Hashes += bm.ColumnHashes[j]
  }

  bm.HashOfColumns = bm.hashOfString(Hashes)
}

func (bm *BlockMatrix) updateRowHashes( From int, To int ) {

  if TraceEnabled { log.Printf("updateRowHashes(%d, %d) called\n", From, To) }

  for i := From; i < To; i++ {

    var Hashes string

    Hashes = ""
    for j := 0; j < bm.Dimension; j++ {

      if i != j { Hashes += bm.BlockHashes[i][j] }
    }

    bm.RowHashes[i] = bm.hashOfString(Hashes)
  }

  bm.updateHashOfRows()
}

func (bm *BlockMatrix) updateColumnHashes( From int, To int ) {

  if TraceEnabled { log.Printf("updateColumnHashes(%d, %d) called\n", From, To) }

  for j := From; j < To; j++ {

    var Hashes string

    Hashes = ""
    for i := 0; i < bm.Dimension; i++ {

      if i != j { Hashes += bm.BlockHashes[i][j] }
    }

    bm.ColumnHashes[j] = bm.hashOfString(Hashes)
  }

  bm.updateHashOfColumns()
}

func (bm *BlockMatrix) updateHashOfMatrix() {

  var Hashes string

  Hashes = ""
  for i := 0; i < bm.Dimension; i++ {

    for j := 0; j < bm.Dimension; j++ {

      if i == j {

        Hashes += bm.BlockHashes[i][j]
      }
    }
  }

  bm.HashOfMatrix = bm.hashOfString(Hashes)
}

func (bm *BlockMatrix) fillDiagonalWithRandomData() error {

  RandomData := make([]byte, RandomBlockLength)

  for i := 0; i < bm.Dimension; i++ {

    for j := 0; j < bm.Dimension; j++ {

      if i == j {

        _, err := rand.Read(RandomData)
        if err != nil { return err }

        bm.BlockData[i][j].Write(RandomData)
        bm.BlockHashes[i][j] = bm.hashOfBytes(bm.BlockData[i][j])

      }
    }
  }

  return nil
}

func (bm *BlockMatrix) InsertBlocks( Blocks []bytes.Buffer ) error {

  var i, j int

  i = 0
  j = 0

  for b := 0; b < len(Blocks); b++ {

    if i == j {

      i = 0
      j++
      b--

    } else if i < j {

      bm.BlockData[i][j] = Blocks[b]
      bm.BlockHashes[i][j] = bm.hashOfBytes(Blocks[b])
      i, j = j, i

    } else if i > j {

      bm.BlockData[i][j] = Blocks[b]
      bm.BlockHashes[i][j] = bm.hashOfBytes(Blocks[b])
      j++
      i, j = j, i

    }
  }

  err := bm.fillDiagonalWithRandomData()
  if err != nil { return err }

  bm.updateRowHashes(0, bm.Dimension)
  bm.updateColumnHashes(0, bm.Dimension)
  bm.updateHashOfMatrix()

  return nil
}

func (bm *BlockMatrix) blockIndex( BlockNumber int ) (i, j int) {

  var s int

  if TraceEnabled { log.Printf("blockIndex(%d) called\n", BlockNumber) }

  if BlockNumber & 0x1 == 0x0 {

    s = int(math.Sqrt(float64(BlockNumber + 1)))

    if BlockNumber <= s * s + s {
      i = s
    } else {
      i = s + 1
    }

    j = (BlockNumber - (i * i - i + 2)) / 2

  } else {

    s = int(math.Sqrt(float64(BlockNumber)))

    if BlockNumber <= s * s + s {
      j = s
    } else {
      j = s + 1
    }

    i = (BlockNumber - (j * j - j + 1)) / 2
  }

  if TraceEnabled { log.Printf("blockIndex() returning %d, %d\n", i, j) }

  return i, j
}

func (bm *BlockMatrix) GetBlockData( BlockNumber int ) bytes.Buffer {

  if TraceEnabled { log.Printf("GetBlockData(%d) called\n", BlockNumber) }

  i, j := bm.blockIndex(BlockNumber)

  if i < 0 || j < 0 || i > bm.Dimension || j > bm.Dimension {

    var EmptyBuf bytes.Buffer

    EmptyBuf.Write([]byte(""))
    return EmptyBuf
  }

  if TraceEnabled { log.Printf("GetBlockData() returning %v\n", &bm.BlockData[i][j]) }

  return bm.BlockData[i][j]
}

func (bm *BlockMatrix) GetBlockHash( BlockNumber int ) string {

  if TraceEnabled { log.Printf("GetBlockHash(%d) called\n", BlockNumber) }

  i, j := bm.blockIndex(BlockNumber)

  if i < 0 || j < 0 || i > bm.Dimension || j > bm.Dimension {

    return ""
  }

  if TraceEnabled { log.Printf("GetBlockHash() returning %v\n", &bm.BlockHashes[i][j]) }

  return bm.BlockHashes[i][j]
}

func (bm *BlockMatrix) GetRowHash( RowNumber int ) string {

  return bm.RowHashes[RowNumber]
}

func (bm *BlockMatrix) GetColHash( ColNumber int ) string {

  return bm.ColumnHashes[ColNumber]
}

func (bm *BlockMatrix) GetHashOfColumns() string {

  return bm.HashOfColumns
}

func (bm *BlockMatrix) GetHashOfRows() string {

  return bm.HashOfRows
}

func (bm *BlockMatrix) DeleteBlock( BlockNumber int ) bool {

  if TraceEnabled { log.Printf("DeleteBlock(%d) called\n", BlockNumber) }

  i, j := bm.blockIndex(BlockNumber)

  if i < 0 || j < 0 || i > bm.Dimension || j > bm.Dimension {

    if TraceEnabled { log.Printf("DeleteBlock() invalid BlockNumber %d\n", BlockNumber) }

    return false
  }

  return bm.deleteBlockAt(i, j)
}

func (bm *BlockMatrix) deleteBlockAt( RowNumber int, ColNumber int ) bool {

  if RowNumber < 0 || ColNumber < 0 || RowNumber > bm.Dimension || ColNumber > bm.Dimension {

    if TraceEnabled { log.Printf("deleteBlockAt() invalid RowNumber %d or ColNumber %d\n", RowNumber, ColNumber) }

    return false
  }

  bm.BlockData[RowNumber][ColNumber].Reset()
  bm.BlockHashes[RowNumber][ColNumber] = ""
  bm.updateRowHashes(RowNumber, RowNumber + 1)
  bm.updateColumnHashes(ColNumber, ColNumber + 1)

  return true
}

func (bm *BlockMatrix) Dump( MaxChars int ) {

  var hs string
  var ds string

  for i := 0; i < bm.Dimension; i++ {

    for j := 0; j < bm.Dimension; j++ {

      if len(bm.BlockHashes[i][j]) < MaxChars {

        hs = "Is block deleted?"
      } else {

        hs = bm.BlockHashes[i][j][0:MaxChars]
      }

      if i != j {

        ds = string(bm.BlockData[i][j].Bytes())
        if ds == "" { ds = "Is block deleted?" }

     } else {

        ds = "RandomData"
      }

      fmt.Printf("i: %d j: %d Data: %s Hash: %s\n", i, j, ds, hs)
    }
  }

  for i := 0; i < bm.Dimension; i++ { fmt.Printf("RowHashes[%d]: %s\n", i, bm.RowHashes[i][0:MaxChars]) }

  for j := 0; j < bm.Dimension; j++ { fmt.Printf("ColumnHashes[%d]: %s\n", j, bm.ColumnHashes[j][0:MaxChars]) }

  fmt.Printf("HashOfRows   : %s\n", bm.HashOfRows[0:MaxChars])
  fmt.Printf("HashOfColumns: %s\n", bm.HashOfColumns[0:MaxChars])
  fmt.Printf("HashOfMatrix : %s\n", bm.HashOfMatrix[0:MaxChars])
}
