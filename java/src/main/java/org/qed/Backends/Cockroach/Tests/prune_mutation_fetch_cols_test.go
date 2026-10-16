package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestPruneMutationFetchCols(t *testing.T) {
	rule := loadRule(t, "PruneMutationFetchCols")
	e := newEnv(t)
	s, cols := e.scan("t", 2)
	z := e.f.Metadata().AddColumn("z", types.Int)
	project := func(input memo.RelExpr, x opt.ScalarExpr) memo.RelExpr {
		return e.f.ConstructProject(input, memo.ProjectionsExpr{e.f.ConstructProjectionsItem(x, z)}, opt.ColSet{})
	}
	fetch := e.f.ConstructUnaryMinus(e.v(cols[0]))
	pruned := e.f.ConstructProject(s, nil, opt.MakeColSet(cols[0]))
	e.expectRewrite(rule, project(s, fetch), project(pruned, fetch))
	e.expectNoMatch(rule, project(s, e.f.ConstructUnaryMinus(e.v(cols[1]))))
	e.expectNoMatch(rule, e.f.ConstructProject(s, memo.ProjectionsExpr{
		e.f.ConstructProjectionsItem(fetch, z),
		e.f.ConstructProjectionsItem(e.v(cols[1]), e.f.Metadata().AddColumn("w", types.Int)),
	}, opt.ColSet{}))
}
