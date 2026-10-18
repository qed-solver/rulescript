package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestFoldFunctionWithNullArg(t *testing.T) {
	rule := loadRule(t, "FoldFunctionWithNullArg")
	e := newEnv(t)
	in, cols := e.scan("t", 1)
	a := cols[0]

	project := func(elem opt.ScalarExpr) memo.RelExpr {
		out := e.f.Metadata().AddColumn("out", types.Bool)
		return e.f.ConstructProject(in, memo.ProjectionsExpr{e.f.ConstructProjectionsItem(elem, out)}, opt.ColSet{})
	}

	before := project(e.f.ConstructEq(e.v(a), e.f.ConstructNull(types.Int)))
	nullOut := e.f.Metadata().AddColumn("nullOut", types.Bool)
	want := e.f.ConstructProject(in, memo.ProjectionsExpr{e.f.ConstructProjectionsItem(e.f.ConstructNull(types.Bool), nullOut)}, opt.ColSet{})
	e.expectRewriteUpToFreshColumns(rule, before, want)

	e.expectNoMatch(rule, project(e.f.ConstructPlus(e.v(a), e.f.ConstructNull(types.Int))))
	e.expectNoMatch(rule, project(e.f.ConstructEq(e.f.ConstructNull(types.Int), e.v(a))))
	e.expectNoMatch(rule, project(e.f.ConstructEq(e.v(a), e.num(1))))
}
