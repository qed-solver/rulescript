package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestPruneScanCols(t *testing.T) {
	e := newEnv(t)
	rule := loadRule(t, "PruneScanCols")
	scan, cols := e.scan("t", 3)
	out := e.f.Metadata().AddColumn("top", types.Int)
	before := e.f.ConstructProject(scan, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.DynamicConstruct(opt.PlusOp, e.v(cols[0]), e.v(cols[1])).(opt.ScalarExpr), out),
	}, opt.ColSet{})
	pruned := e.f.ConstructProject(scan, memo.EmptyProjectionsExpr, opt.MakeColSet(cols[0], cols[1]))
	want := e.f.ConstructProject(pruned, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.DynamicConstruct(opt.PlusOp, e.v(cols[0]), e.v(cols[1])).(opt.ScalarExpr), out),
	}, opt.ColSet{})
	e.expectRewrite(rule, before, want)

	nearMiss := e.f.ConstructProject(scan, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(e.f.DynamicConstruct(opt.PlusOp, e.v(cols[0]), e.v(cols[2])).(opt.ScalarExpr), out),
	}, opt.ColSet{})
	e.expectNoMatch(rule, nearMiss)
}
