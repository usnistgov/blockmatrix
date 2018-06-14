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

/*

The draft version of the NIST paper used null for diagonal values
But I used random data in diagonal cells

The draft version of the NIST paper did not have hash of rows and hash of columns
I added them for extra security

Also, the draft version did not have a hash of matrix
I added it to store the hash of random data in diagonal cells

*/

type BlockMatrix struct {

  Dimension int                // N x N matrix
  HashAlgorithm string         // Hash algorithm to use for the matrix
  BlockData [][]bytes.Buffer   // Block data
  BlockHashes [][]string       // Hash of block data
  RowHashes []string           // Row hashes
  HashOfRows string            // Hash of all row hashes
  ColumnHashes []string        // Column hashes
  HashOfColumns string         // Hash of all column hashes
  HashOfMatrix string          // Hash of diagonal elements
}

// Developers can use this variable to trace program execution
var TraceEnabled bool = false

const (

  // Hash algorithms to be used in hashing data
  Sha256 = "SHA256"
  Sha384 = "SHA384"
  Sha512 = "SHA512"

  // Diagonal cells are filled with random data, this is the length of random blocks
  RandomBlockLength = 64
)

// Create a BlockMatrix data structure and set its dimension and hash algorithm
// Allocate arrays in the BlockMatrix
// Fill diagonal cells with random data

func Create( Dimension int, HashAlgorithm string ) *BlockMatrix {

  if TraceEnabled { log.Printf("Create(%d, %s) called\n", Dimension, HashAlgorithm) }

  bm := new(BlockMatrix)
  bm.Dimension = Dimension
  bm.HashAlgorithm = HashAlgorithm
  bm.BlockData = make([][]bytes.Buffer, Dimension)
  for i := 0; i < Dimension; i++ { bm.BlockData[i] = make([]bytes.Buffer, Dimension) }

  bm.BlockHashes = make([][]string, Dimension)
  for i := 0; i < Dimension; i++ { bm.BlockHashes[i] = make([]string, Dimension) }

  bm.RowHashes = make([]string, Dimension)
  bm.ColumnHashes = make([]string, Dimension)

  bm.fillDiagonalWithRandomData()
  bm.updateHashOfMatrix()

  if TraceEnabled { log.Printf("Create() returning %v\n", &bm) }

  return bm
}

// an internal function to compute the hash of a stream of bytes

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

// an internal function to compute the hash of a given string

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

// an internal function to compute/update the hash of all rows's hashes
// Each row's hash is concatenated and the resulting string is hashed

func (bm *BlockMatrix) updateHashOfRows() {

  var Hashes string

  Hashes = ""
  for i := 0; i < bm.Dimension; i++ {

    Hashes += bm.RowHashes[i]
  }

  bm.HashOfRows = bm.hashOfString(Hashes)
}

// an internal function to compute/update the hash of all column's hashes
// Each column's hash is concatenated and the resulting string is hashed

func (bm *BlockMatrix) updateHashOfColumns() {

  var Hashes string

  Hashes = ""
  for j := 0; j < bm.Dimension; j++ {

    Hashes += bm.ColumnHashes[j]
  }

  bm.HashOfColumns = bm.hashOfString(Hashes)
}

// an internal function to compute/update row hashes
// From and To specify the range to update
// If you want to update row 3's hash, then use From = 3 and To = 4
// Diagonal elements (i = j) are excluded in hash computation

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

// an internal function to compute/update column hashes
// From and To specify the range to update
// If you want to update column 3's hash, then use From = 3 and To = 4
// Diagonal elements (i = j) are excluded in hash computation

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

// an internal function to update the hash of matrix
// It concatenates the hashes of random data in diagonal cells and hashes the result
// It's also a unique identifier of each block matrix

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

// an internal function to fill diagonal cells with random data
// Each random data block has a fixed length of RandomBlockLength bytes
// If there is a problem in rand.Read(), then error is returned to the caller

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

// Insert N * N - N blocks into the block matrix
// Since diagonal cells are filled with random data we have N * N - N available blocks

func (bm *BlockMatrix) InsertBlocks( Blocks []bytes.Buffer ) {

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

  bm.updateRowHashes(0, bm.Dimension)
  bm.updateColumnHashes(0, bm.Dimension)
  bm.updateHashOfMatrix()
}

// an internal function to return row and column number of a given block
// The block matrix is filled with an array of blocks
// This function maps blocks to matrix coordinates

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

// Given a block number, its data is returned
// If there is no such block, an empty block is returned to the caller

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

// Given a block number, its hash string is returned
// If there is no such block, an empty string is returned to the caller

func (bm *BlockMatrix) GetBlockHash( BlockNumber int ) string {

  if TraceEnabled { log.Printf("GetBlockHash(%d) called\n", BlockNumber) }

  i, j := bm.blockIndex(BlockNumber)

  if i < 0 || j < 0 || i > bm.Dimension || j > bm.Dimension {

    return ""
  }

  if TraceEnabled { log.Printf("GetBlockHash() returning %v\n", &bm.BlockHashes[i][j]) }

  return bm.BlockHashes[i][j]
}

// returns the hash of a given row

func (bm *BlockMatrix) GetRowHash( RowNumber int ) string {

  return bm.RowHashes[RowNumber]
}

// returns the hash of a given column

func (bm *BlockMatrix) GetColHash( ColNumber int ) string {

  return bm.ColumnHashes[ColNumber]
}

// returns the hash of all columns

func (bm *BlockMatrix) GetHashOfColumns() string {

  return bm.HashOfColumns
}

// returns the has of all rows
func (bm *BlockMatrix) GetHashOfRows() string {

  return bm.HashOfRows
}

// GPDR complaint block deletion using block number

func (bm *BlockMatrix) DeleteBlock( BlockNumber int ) bool {

  if TraceEnabled { log.Printf("DeleteBlock(%d) called\n", BlockNumber) }

  i, j := bm.blockIndex(BlockNumber)

  if i < 0 || j < 0 || i > bm.Dimension || j > bm.Dimension {

    if TraceEnabled { log.Printf("DeleteBlock() invalid BlockNumber %d\n", BlockNumber) }

    return false
  }

  return bm.deleteBlockAt(i, j)
}

// GPDR complaint block deletion using row and column numbers
// Block data is reset and its hash is set to empty string
// Affected row and column hashes are updated

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

// Dumps a block matrix, usefull for debugging
// Only the first MaxChars bytes of hashes are printed

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
