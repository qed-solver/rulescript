package rulescript_test

import (
	"testing"

	"github.com/cockroachdb/cockroach/pkg/sql/opt"
	"github.com/cockroachdb/cockroach/pkg/sql/opt/memo"
	"github.com/cockroachdb/cockroach/pkg/sql/types"
)

func TestUnionToDistinct(t *testing.T) {
	e := newEnv(t)
	rule := loadRule(t, "UnionToDistinct")
	left, leftCols := e.scan("t", 1)
	right, rightCols := e.scan("u", 1)
	out := e.f.Metadata().AddColumn("union", types.Int)
	private := &memo.SetPrivate{
		LeftCols:  opt.ColList{leftCols[0]},
		RightCols: opt.ColList{rightCols[0]},
		OutCols:   opt.ColList{out},
	}
	before := e.f.ConstructUnion(left, right, private)
	all := e.f.ConstructUnionAll(left, right, &memo.SetPrivate{
		LeftCols:  opt.ColList{leftCols[0]},
		RightCols: opt.ColList{rightCols[0]},
		OutCols:   opt.ColList{out},
	})
	want := e.f.ConstructGroupBy(all, nil, &memo.GroupingPrivate{GroupingCols: opt.MakeColSet(out)})
	e.expectRewrite(rule, before, want)
	e.expectNoMatch(rule, all)
}
