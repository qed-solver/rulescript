package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
)

func TestPruneMutationReturnCols(t *testing.T) {
	rule := loadRule(t, "PruneMutationReturnCols")
	e := newEnv(t)
	if _, err := e.cat.ExecuteDDL("CREATE TABLE wide (a INT PRIMARY KEY, b INT, c INT, d INT)"); err != nil {
		t.Fatal(err)
	}
	input, cols := e.scan("wide", 4)
	sum := func() opt.ScalarExpr {
		return e.f.ConstructPlus(e.f.ConstructPlus(e.f.ConstructPlus(e.v(cols[0]), e.v(cols[1])), e.v(cols[2])), e.v(cols[3]))
	}
	ret := []opt.ColumnID{
		e.f.Metadata().AddColumn("r0", e.v(cols[0]).DataType()),
		e.f.Metadata().AddColumn("r1", e.v(cols[0]).DataType()),
		e.f.Metadata().AddColumn("r2", e.v(cols[0]).DataType()),
		e.f.Metadata().AddColumn("r3", e.v(cols[0]).DataType()),
	}
	returned := e.f.ConstructProject(input, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(sum(), ret[0]),
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(sum(), e.num(1)), ret[1]),
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(sum(), e.num(2)), ret[2]),
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(sum(), e.num(3)), ret[3]),
	}, opt.ColSet{})
	out := e.f.Metadata().AddColumn("out", e.v(ret[0]).DataType())
	before := e.f.ConstructProject(returned, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructUnaryMinus(e.v(ret[0])), out),
	}, opt.MakeColSet(ret[3]))

	pruned := e.f.ConstructProject(input, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(sum(), ret[0]),
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(sum(), e.num(2)), ret[2]),
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(sum(), e.num(3)), ret[3]),
	}, opt.ColSet{})
	want := e.f.ConstructProject(pruned, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructUnaryMinus(e.v(ret[0])), out),
	}, opt.MakeColSet(ret[3]))
	e.expectRewriteUpToFreshColumns(rule, before, want)

	shortInput, shortCols := e.scan("t", 3)
	nearSum := e.f.ConstructPlus(e.f.ConstructPlus(e.v(shortCols[0]), e.v(shortCols[1])), e.v(shortCols[2]))
	nearRet := []opt.ColumnID{
		e.f.Metadata().AddColumn("r0", e.v(shortCols[0]).DataType()),
		e.f.Metadata().AddColumn("r1", e.v(shortCols[0]).DataType()),
		e.f.Metadata().AddColumn("r2", e.v(shortCols[0]).DataType()),
	}
	nearReturned := e.f.ConstructProject(shortInput, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(nearSum, nearRet[0]),
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(nearSum, e.num(1)), nearRet[1]),
		e.f.ConstructProjectionsItem(e.f.ConstructPlus(nearSum, e.num(2)), nearRet[2]),
	}, opt.ColSet{})
	nearOut := e.f.Metadata().AddColumn("out", e.v(nearRet[0]).DataType())
	nearMiss := e.f.ConstructProject(nearReturned, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.ConstructUnaryMinus(e.v(nearRet[0])), nearOut),
	}, opt.MakeColSet(nearRet[2]))
	e.expectNoMatch(rule, nearMiss)
}
