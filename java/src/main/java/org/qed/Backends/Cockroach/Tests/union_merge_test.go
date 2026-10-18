package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestUnionMerge(t *testing.T) {
	e := newEnv(t)
	rule := loadRule(t, "UnionMerge")
	a, ac := e.scan("t", 1)
	b, bc := e.scan("u", 1)
	c, cc := e.scan("t", 1)
	d, dc := e.scan("u", 1)
	union := func(left memo.RelExpr, leftCol opt.ColumnID, right memo.RelExpr, rightCol opt.ColumnID) (memo.RelExpr, opt.ColumnID) {
		out := e.f.Metadata().AddColumn("union", types.Int)
		return e.f.ConstructUnionAll(left, right, &memo.SetPrivate{
			LeftCols: opt.ColList{leftCol}, RightCols: opt.ColList{rightCol}, OutCols: opt.ColList{out},
		}), out
	}
	bcRel, bcCol := union(b, bc[0], c, cc[0])
	abc, abcCol := union(a, ac[0], bcRel, bcCol)
	before, _ := union(abc, abcCol, d, dc[0])
	ab, abCol := union(a, ac[0], b, bc[0])
	abcWant, abcWantCol := union(ab, abCol, c, cc[0])
	want, _ := union(abcWant, abcWantCol, d, dc[0])
	e.expectRewrite(rule, before, want)
	cd, cdCol := union(c, cc[0], d, dc[0])
	nearMiss, _ := union(ab, abCol, cd, cdCol)
	e.expectNoMatch(rule, nearMiss)
}
