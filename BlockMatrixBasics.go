package main

import(
  "fmt"
  "bytes"
  "blockmatrix"
)

// This is an application to show basic usage of Block Matrix data structure

func main() {

  var Dimension int = 5  // Dimension of block matrix
  var Blocks []bytes.Buffer  // data blocks

  Blocks = make([]bytes.Buffer, Dimension * Dimension - Dimension)
  for b := 0; b < len(Blocks); b++ {

    s := fmt.Sprintf("This is Block No: %d", b + 1)
    Blocks[b].Write([]byte(s))
  }

  // for developers to trace functions, set TraceEnabled to true
  blockmatrix.TraceEnabled = true

  // Create a block matrix of specified dimension
  bm := blockmatrix.Create(Dimension, blockmatrix.Sha256)
  fmt.Printf("Created a block matrix %v\n", &bm)

  // Dump the block matrix and print only the first 8 characters of hashes
  bm.Dump(8)

  // Now insert all our blocks into the block matrix
  bm.InsertBlocks(Blocks)

  // Dump the block matrix and print only the first 8 characters of hashes
  bm.Dump(8)

  // Get block data of blocknumber = 12
  bd := bm.GetBlockData(12)
  fmt.Printf("GetBlockData 12: %s\n", bd.String())
  fmt.Printf("GetBlockHash 12: %s\n", bm.GetBlockHash(12))

  // now delete block number 12
  bm.DeleteBlock(12)
  bd = bm.GetBlockData(12)
  fmt.Printf("GetBlockData 12: %s\n", bd.String())
  fmt.Printf("GetBlockHash 12: %s\n", bm.GetBlockHash(12))

  // Dump the block matrix and print only the first 8 characters of hashes
  bm.Dump(8)
}
