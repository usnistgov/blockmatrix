# blockmatrix
This project is developing code to implement features and extensions to the NIST Cybersecurity Whitepaper, "A Data Structure for Integrity Protection with Erasure Capability". The block matrix data structure may have utility for incorporation into applications requiring integrity protection that currently use permissioned blockchains. This capability could for example be useful in meeting privacy requirements such as the European Union General Data Protection Regulation (GDPR), which requires that organizations make it possible to delete all information related to a particular individual, at that person's request. 

# In This Repo 
This repo consists of 2 main parts: A Block Matrix Data Structure written in Go, and a Java package which uses a Block Matrix to implement a blockchain. The two parts are both based off the block matrices descirbed in the NIST Whitepaper, but are otherwise completely unrelated. The source code for the blockmatrix blockchain package is in the blockmatrixChain folder. A presentation explaining the block matrix blockchain package, along with a manual explaining how to use it and the concept in more detail, is included in the docs subfolder of the blockmatrixChain folder. The Go Block Matrix Data Structure is described in the rest of this README. 

# Block Matrix Data Structure
In addition to the specifications in the draft version of NIST Cybersecurity Whitepaper, "A Data Structure for Integrity Protection with Erasure Capability", the following changes are introduced:<br>
* Diagonal elements contain random data instead of empty blocks (**fillDiagonalWithRandomData() function**)
* The hash of diagonal elements' hashes are used to identify a block matrix uniquely (**HashOfMatrix**)
* The hash of column hashes are introduced (**HashOfColumns**)
* The hash of row hashes are introduced (**HashOfRows**)
* Read-Write locks are introduced for synchronization purposes (**RowLocks and ColumnLocks**)<br>
        
```go
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
  RowLocks []sync.RWMutex      // Read-Write locks for rows
  ColumnLocks []sync.RWMutex   // Read-Write locks for columns
}
```
# Functions and Methods
        
```go
func Create( Dimension int, HashAlgorithm string ) *BlockMatrix
```
Creates a BlockMatrix data structure and set its dimension and hash algorithm, allocates arrays in the BlockMatrix and fills diagonal cells with random data<br>
        
```go
func (bm *BlockMatrix) InsertBlocks( Blocks []bytes.Buffer )
```
Inserts N * N - N blocks into the block matrix. Since diagonal cells are filled with random data, we have N * N - N available blocks<br>
        
```go
func (bm *BlockMatrix) GetBlockData( BlockNumber int ) bytes.Buffer
```
Given a block number, its data is returned. If there is no such block, an empty block is returned to the caller<br>
        
```go
func (bm *BlockMatrix) GetBlockHash( BlockNumber int ) string
```
Given a block number, its hash is returned. If there is no such block, an empty string is returned to the caller<br>
        
```go
func (bm *BlockMatrix) DeleteBlock( BlockNumber int ) bool
```
GPDR compliant block deletion using block number<br>
        
```go
func (bm *BlockMatrix) Dump( MaxChars int )
```
Dumps a block matrix, useful for debugging. Only the first MaxChars bytes of hashes are printed<br>
        
```go
func (bm *BlockMatrix) GetRowHash( RowNumber int ) string
```
Returns the hash of a given row<br>
        
```go
func (bm *BlockMatrix) GetColumnHash( ColNumber int ) string
```
Returns the hash of a given column<br>
        
```go
func (bm *BlockMatrix) GetHashOfColumns() string
```
Returns the hash of all columns' hashes<br>
        
```go
func (bm *BlockMatrix) GetHashOfRows() string
```
Returns the hash of all rows' hashes<br>
        
```go
func (bm *BlockMatrix) GetHashOfMatrix() string
```
Returns the hash of diagonal data<br><br>

# A Sample Program, BlockMatrixBasics.go       
        
```go
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
```
