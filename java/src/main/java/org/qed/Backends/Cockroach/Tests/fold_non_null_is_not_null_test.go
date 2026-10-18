package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/sem/catconstants"
	"github.com/cockroachdb/cockroach/pkg/sql/sem/tree"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestFoldNonNullIsNotNull(t *testing.T) {
	rule := loadRule(t, "FoldNonNullIsNotNull")
	e := newEnv(t)

	nn, nnCols := e.scan("nn", 1)
	isNotNull := e.f.ConstructIsNot(e.v(nnCols[0]), e.f.ConstructNull(types.Int))
	before := e.f.ConstructSelect(nn, e.filters(isNotNull))
	e.expectRewrite(rule, before, nn)

	isNull := e.f.ConstructIs(e.v(nnCols[0]), e.f.ConstructNull(types.Int))
	e.expectNoMatch(rule, e.f.ConstructSelect(nn, e.filters(isNull)))

	nn2, nn2Cols := e.scan("nn", 2)
	e.expectNoMatch(rule, e.f.ConstructSelect(nn2, e.filters(
		e.f.ConstructIsNot(e.v(nn2Cols[0]), e.f.ConstructNull(types.Int)),
	)))

	tn := tree.NewTableNameWithSchema("t", catconstants.PublicSchemaName, "t")
	tabID := e.f.Metadata().AddTable(e.cat.Table(tn), tn)
	nullableScan := e.f.ConstructScan(&memo.ScanPrivate{
		Table: tabID,
		Cols:  opt.MakeColSet(tabID.ColumnID(1)),
	})
	nullableCol := tabID.ColumnID(1)
	nullableCond := e.f.ConstructIsNot(e.v(nullableCol), e.f.ConstructNull(types.Int))
	e.expectNoMatch(rule, e.f.ConstructSelect(nullableScan, e.filters(nullableCond)))
}
